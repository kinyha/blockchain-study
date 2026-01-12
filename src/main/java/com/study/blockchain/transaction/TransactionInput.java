package com.study.blockchain.transaction;

import java.security.*;

/**
 * Вход транзакции — ссылка на UTXO, который тратится.
 * Задание: lessons/05-transactions.md, lessons/07-signatures.md
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

    // Урок 7: Подпись транзакций
    public void sign(PrivateKey privateKey) {
        // TODO: Подпиши transactionOutputId приватным ключом
        // Signature sig = Signature.getInstance("SHA256withECDSA");
        // sig.initSign(privateKey);
        // sig.update(transactionOutputId.getBytes());
        // this.signature = sig.sign();
        throw new UnsupportedOperationException("Реализуй sign (Урок 7)");
    }

    public boolean verifySignature(PublicKey publicKey) {
        // TODO: Проверь подпись публичным ключом
        throw new UnsupportedOperationException("Реализуй verifySignature (Урок 7)");
    }
}
