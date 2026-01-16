package com.study.blockchain.service;

import com.study.blockchain.transaction.Transaction;

/**
 * Result of transaction creation attempt.
 * Contains either a successful transaction or an error message.
 */
public class TransactionResult {

    private final boolean success;
    private final Transaction transaction;
    private final String error;

    private TransactionResult(boolean success, Transaction transaction, String error) {
        this.success = success;
        this.transaction = transaction;
        this.error = error;
    }

    public static TransactionResult success(Transaction transaction) {
        return new TransactionResult(true, transaction, null);
    }

    public static TransactionResult failure(String error) {
        return new TransactionResult(false, null, error);
    }

    public boolean isSuccess() {
        return success;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public String getError() {
        return error;
    }
}
