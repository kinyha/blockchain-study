package com.study.blockchain.transaction;

/**
 * Вход транзакции — ссылка на UTXO, который тратится.
 * Задание: lessons/05-transactions.md
 */
public class TransactionInput {

    private String transactionOutputId;
    private TransactionOutput UTXO;
    private byte[] signature;

    public TransactionInput(String transactionOutputId) {
        // TODO: Инициализируй transactionOutputId
        throw new UnsupportedOperationException("Реализуй конструктор TransactionInput");
    }

    public String getTransactionOutputId() {
        return transactionOutputId;
    }

    public TransactionOutput getUTXO() {
        return UTXO;
    }

    public void setUTXO(TransactionOutput UTXO) {
        this.UTXO = UTXO;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }
}
