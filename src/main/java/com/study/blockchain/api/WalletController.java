package com.study.blockchain.api;

import com.study.blockchain.api.dto.UtxoDto;
import com.study.blockchain.api.dto.WalletInfoDto;
import com.study.blockchain.service.BlockchainService;
import com.study.blockchain.wallet.Wallet;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for wallet operations.
 */
@RestController
@RequestMapping("/api/wallet")
@Profile("!orchestrator")
public class WalletController {

    private final BlockchainService blockchainService;
    private final Wallet nodeWallet;

    public WalletController(BlockchainService blockchainService, Wallet nodeWallet) {
        this.blockchainService = blockchainService;
        this.nodeWallet = nodeWallet;
    }

    /**
     * Get wallet address.
     */
    @GetMapping("/address")
    public String getAddress() {
        return blockchainService.getWalletAddress(nodeWallet);
    }

    /**
     * Get wallet balance.
     */
    @GetMapping("/balance")
    public double getBalance() {
        return blockchainService.getBalance(nodeWallet);
    }

    /**
     * Get all UTXOs owned by this wallet.
     */
    @GetMapping("/utxos")
    public List<UtxoDto> getUtxos() {
        return blockchainService.getUtxos(nodeWallet).stream()
                .map(UtxoDto::from)
                .toList();
    }

    /**
     * Get full wallet info.
     */
    @GetMapping
    public WalletInfoDto getWalletInfo() {
        return new WalletInfoDto(
                blockchainService.getWalletAddress(nodeWallet),
                blockchainService.getBalance(nodeWallet),
                getUtxos()
        );
    }
}
