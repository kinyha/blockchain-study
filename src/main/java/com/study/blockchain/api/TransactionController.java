package com.study.blockchain.api;

import com.study.blockchain.api.dto.TransactionDto;
import com.study.blockchain.api.dto.TransactionResultDto;
import com.study.blockchain.service.BlockchainService;
import com.study.blockchain.service.TransactionResult;
import com.study.blockchain.wallet.Wallet;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.PublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * REST controller for transaction operations.
 */
@RestController
@RequestMapping("/api/transactions")
@Profile("!orchestrator")
public class TransactionController {

    private final BlockchainService blockchainService;
    private final Wallet nodeWallet;

    public TransactionController(BlockchainService blockchainService, Wallet nodeWallet) {
        this.blockchainService = blockchainService;
        this.nodeWallet = nodeWallet;
    }

    /**
     * Create a new transaction from this node's wallet.
     */
    @PostMapping
    public ResponseEntity<TransactionResultDto> createTransaction(@RequestBody TransactionDto dto) {
        try {
            // For now, we use a simplified address lookup
            // In production, this would involve proper address resolution
            PublicKey recipient = resolveAddress(dto.recipientAddress());
            if (recipient == null) {
                return ResponseEntity.badRequest()
                        .body(TransactionResultDto.failure("Invalid recipient address"));
            }

            TransactionResult result = blockchainService.createTransaction(
                    nodeWallet, recipient, dto.amount());

            if (result.isSuccess()) {
                return ResponseEntity.ok(
                        TransactionResultDto.success(result.getTransaction().getTransactionId()));
            } else {
                return ResponseEntity.badRequest()
                        .body(TransactionResultDto.failure(result.getError()));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(TransactionResultDto.failure("Failed to create transaction: " + e.getMessage()));
        }
    }

    /**
     * Get all pending transactions in mempool.
     */
    @GetMapping("/pending")
    public List<Map<String, Object>> getPendingTransactions() {
        return blockchainService.getPendingTransactions().stream()
                .map(tx -> Map.<String, Object>of(
                        "transactionId", tx.getTransactionId(),
                        "inputCount", tx.getInputs().size(),
                        "outputCount", tx.getOutputs().size()
                ))
                .toList();
    }

    /**
     * Get pending transaction count.
     */
    @GetMapping("/pending/count")
    public int getPendingCount() {
        return blockchainService.getPendingTransactionsCount();
    }

    private PublicKey resolveAddress(String address) {
        // Simplified: in production would use proper key reconstruction
        // For demo purposes, we'll need a peer registry
        return null; // Will be implemented with peer discovery
    }
}
