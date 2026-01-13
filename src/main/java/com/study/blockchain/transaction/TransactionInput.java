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
        this.transactionOutputId = transactionOutputId;
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
        try {
            Signature sig = Signature.getInstance("SHA256withECDSA");
            sig.initSign(privateKey);
            sig.update(transactionOutputId.getBytes());
            this.signature = sig.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new RuntimeException("Signing failed", e);
        }
    }

    public boolean verifySignature(PublicKey publicKey) {
        if (signature == null) {
            return false;
        }
        try {
            Signature sig = Signature.getInstance("SHA256withECDSA");
            sig.initVerify(publicKey);
            sig.update(transactionOutputId.getBytes());
            return sig.verify(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            return false;
        }
    }
}
