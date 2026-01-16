package com.study.blockchain.service;

import com.study.blockchain.transaction.Transaction;
import com.study.blockchain.utxo.UTXOPool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing pending transactions (mempool).
 *
 * Responsibilities:
 * - Store pending transactions awaiting mining
 * - Remove transactions when they are included in a block
 * - Return orphan block transactions to the pool
 */
public class MempoolService {

    private final Map<String, Transaction> pendingTransactions;
    private final UTXOPool utxoPool;

    public MempoolService(UTXOPool utxoPool) {
        this.pendingTransactions = new LinkedHashMap<>(); // Maintains insertion order
        this.utxoPool = utxoPool;
    }

    /**
     * Adds a transaction to the mempool.
     *
     * @param transaction Transaction to add
     * @return true if added, false if already exists or invalid
     */
    public boolean addTransaction(Transaction transaction) {
        if (transaction.getTransactionId() == null) {
            return false;
        }

        // Check if already in mempool
        if (pendingTransactions.containsKey(transaction.getTransactionId())) {
            return false;
        }

        // Transaction must have inputs (coinbase transactions don't go through mempool)
        if (transaction.getInputs().isEmpty()) {
            return false;
        }

        // Verify signatures
        if (!transaction.verifySignatures()) {
            return false;
        }

        pendingTransactions.put(transaction.getTransactionId(), transaction);
        return true;
    }

    /**
     * Removes a transaction from the mempool.
     *
     * @param transactionId ID of the transaction to remove
     */
    public void removeTransaction(String transactionId) {
        Transaction removed = pendingTransactions.remove(transactionId);
        if (removed != null) {
            // Release UTXO reservations
            releaseReservations(removed);
        }
    }

    /**
     * Removes multiple transactions from the mempool (after they are mined).
     *
     * @param transactions List of transactions to remove
     */
    public void removeTransactions(List<Transaction> transactions) {
        for (Transaction tx : transactions) {
            pendingTransactions.remove(tx.getTransactionId());
            // Don't release reservations - UTXOs are now spent
        }
    }

    /**
     * Returns transactions from an orphan block back to the mempool.
     *
     * @param transactions Transactions from the orphan block
     */
    public void returnToMempool(List<Transaction> transactions) {
        for (Transaction tx : transactions) {
            // Only add if not already present
            if (!pendingTransactions.containsKey(tx.getTransactionId())) {
                pendingTransactions.put(tx.getTransactionId(), tx);
            }
        }
    }

    /**
     * Gets all pending transactions.
     *
     * @return Copy of the pending transactions list
     */
    public List<Transaction> getPendingTransactions() {
        return new ArrayList<>(pendingTransactions.values());
    }

    /**
     * Gets transactions for inclusion in a block.
     *
     * @param maxCount Maximum number of transactions to return
     * @return List of transactions for the block
     */
    public List<Transaction> getTransactionsForBlock(int maxCount) {
        List<Transaction> result = new ArrayList<>();
        int count = 0;
        for (Transaction tx : pendingTransactions.values()) {
            if (count >= maxCount) {
                break;
            }
            result.add(tx);
            count++;
        }
        return result;
    }

    /**
     * Checks if a transaction is in the mempool.
     *
     * @param transactionId ID of the transaction
     * @return true if present
     */
    public boolean contains(String transactionId) {
        return pendingTransactions.containsKey(transactionId);
    }

    /**
     * Gets the number of pending transactions.
     *
     * @return Size of the mempool
     */
    public int size() {
        return pendingTransactions.size();
    }

    /**
     * Clears all pending transactions.
     */
    public void clear() {
        for (Transaction tx : pendingTransactions.values()) {
            releaseReservations(tx);
        }
        pendingTransactions.clear();
    }

    private void releaseReservations(Transaction tx) {
        for (var input : tx.getInputs()) {
            utxoPool.releaseReservation(input.getTransactionOutputId());
        }
    }
}
