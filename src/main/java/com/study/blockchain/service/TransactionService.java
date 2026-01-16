package com.study.blockchain.service;

import com.study.blockchain.transaction.Transaction;
import com.study.blockchain.transaction.TransactionInput;
import com.study.blockchain.transaction.TransactionOutput;
import com.study.blockchain.utxo.UTXOPool;
import com.study.blockchain.wallet.Wallet;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for creating and validating transactions.
 *
 * Business rules:
 * - Fee: 0.1 coins (fixed)
 * - Min amount: 0.1 coins
 * - Reject if: balance < amount + fee
 */
public class TransactionService {

    public static final double TRANSACTION_FEE = 0.1;
    public static final double MIN_TRANSACTION_AMOUNT = 0.1;

    private final UTXOPool utxoPool;

    public TransactionService(UTXOPool utxoPool) {
        this.utxoPool = utxoPool;
    }

    /**
     * Creates a new transaction from sender to recipient.
     *
     * @param sender    Wallet of the sender
     * @param recipient Public key of the recipient
     * @param amount    Amount to send
     * @return TransactionResult with transaction or error
     */
    public TransactionResult createTransaction(Wallet sender, PublicKey recipient, double amount) {
        // Validate amount is positive
        if (amount <= 0) {
            return TransactionResult.failure("Amount must be positive");
        }

        // Validate minimum amount
        if (amount < MIN_TRANSACTION_AMOUNT) {
            return TransactionResult.failure("Amount below minimum: " + MIN_TRANSACTION_AMOUNT);
        }

        double requiredAmount = amount + TRANSACTION_FEE;
        double availableBalance = utxoPool.getAvailableBalance(sender.getPublicKey());

        // Validate sufficient balance
        if (availableBalance < requiredAmount) {
            return TransactionResult.failure(String.format(
                    "Insufficient balance: have %.1f, need %.1f", availableBalance, requiredAmount));
        }

        // Select UTXOs to spend
        List<TransactionOutput> selectedUtxos = selectUtxos(sender.getPublicKey(), requiredAmount);

        // Create transaction
        Transaction tx = new Transaction();

        // Add inputs
        double totalInput = 0;
        for (TransactionOutput utxo : selectedUtxos) {
            TransactionInput input = new TransactionInput(utxo.getId());
            input.setUTXO(utxo);
            tx.addInput(input);
            totalInput += utxo.getAmount();

            // Reserve the UTXO
            utxoPool.reserveUTXO(utxo.getId());
        }

        // Add output for recipient
        tx.addOutput(new TransactionOutput(recipient, amount, "pending"));

        // Add change output if needed
        double change = totalInput - amount - TRANSACTION_FEE;
        if (change > 0.0001) { // Small epsilon for floating point comparison
            tx.addOutput(new TransactionOutput(sender.getPublicKey(), change, "pending"));
        }

        // Sign all inputs
        tx.signAllInputs(sender.getPrivateKey());

        // Finalize transaction (set ID and update output IDs)
        tx.finalizeTransaction();

        return TransactionResult.success(tx);
    }

    /**
     * Selects UTXOs to cover the required amount.
     * Uses simple greedy algorithm - takes UTXOs until we have enough.
     */
    private List<TransactionOutput> selectUtxos(PublicKey owner, double requiredAmount) {
        List<TransactionOutput> selected = new ArrayList<>();
        double collected = 0;

        for (TransactionOutput utxo : utxoPool.getUTXOsForAddress(owner)) {
            if (collected >= requiredAmount) {
                break;
            }
            selected.add(utxo);
            collected += utxo.getAmount();
        }

        return selected;
    }
}
