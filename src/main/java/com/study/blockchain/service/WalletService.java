package com.study.blockchain.service;

import com.study.blockchain.transaction.TransactionOutput;
import com.study.blockchain.utxo.UTXOPool;
import com.study.blockchain.wallet.Wallet;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Service for wallet operations.
 *
 * Responsibilities:
 * - Calculate wallet balance from UTXOs
 * - Retrieve wallet's UTXOs
 * - Select UTXOs for spending
 * - Generate wallet address
 */
public class WalletService {

    private final UTXOPool utxoPool;

    public WalletService(UTXOPool utxoPool) {
        this.utxoPool = utxoPool;
    }

    /**
     * Gets the balance of a wallet.
     * Balance is the sum of all unspent, unreserved UTXOs owned by the wallet.
     *
     * @param wallet Wallet to check
     * @return Balance in coins
     */
    public double getBalance(Wallet wallet) {
        return getAvailableUtxos(wallet).stream()
                .mapToDouble(TransactionOutput::getAmount)
                .sum();
    }

    /**
     * Gets all UTXOs owned by the wallet (including reserved).
     *
     * @param wallet Wallet to check
     * @return List of UTXOs
     */
    public List<TransactionOutput> getUtxos(Wallet wallet) {
        PublicKey publicKey = wallet.getPublicKey();
        List<TransactionOutput> result = new ArrayList<>();

        for (TransactionOutput utxo : utxoPool.getAllUTXOs()) {
            if (utxo.isMine(publicKey)) {
                result.add(utxo);
            }
        }

        return result;
    }

    /**
     * Gets available (not reserved) UTXOs owned by the wallet.
     *
     * @param wallet Wallet to check
     * @return List of available UTXOs
     */
    public List<TransactionOutput> getAvailableUtxos(Wallet wallet) {
        // getUTXOsForAddress already filters out reserved UTXOs
        return utxoPool.getUTXOsForAddress(wallet.getPublicKey());
    }

    /**
     * Selects UTXOs to cover the required amount.
     * Uses a simple greedy algorithm - selects UTXOs until the amount is covered.
     *
     * @param wallet Wallet to select from
     * @param amount Amount needed
     * @return List of UTXOs to use, or null if insufficient balance
     */
    public List<TransactionOutput> selectUtxosForAmount(Wallet wallet, double amount) {
        if (amount <= 0) {
            return new ArrayList<>();
        }

        List<TransactionOutput> available = getAvailableUtxos(wallet);
        List<TransactionOutput> selected = new ArrayList<>();
        double total = 0;

        // First, try to find exact match
        for (TransactionOutput utxo : available) {
            if (Math.abs(utxo.getAmount() - amount) < 0.0001) {
                selected.add(utxo);
                return selected;
            }
        }

        // Greedy selection - largest first
        available.sort((a, b) -> Double.compare(b.getAmount(), a.getAmount()));

        for (TransactionOutput utxo : available) {
            selected.add(utxo);
            total += utxo.getAmount();

            if (total >= amount) {
                return selected;
            }
        }

        // Insufficient balance
        return null;
    }

    /**
     * Gets a human-readable address for the wallet.
     * Uses Base64 encoding of the public key.
     *
     * @param wallet Wallet to get address for
     * @return Address string
     */
    public String getAddress(Wallet wallet) {
        byte[] encoded = wallet.getPublicKey().getEncoded();
        return Base64.getEncoder().encodeToString(encoded);
    }
}
