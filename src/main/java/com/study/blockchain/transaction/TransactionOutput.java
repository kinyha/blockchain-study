package com.study.blockchain.transaction;

import com.study.blockchain.core.HashUtil;
import java.security.PublicKey;
import java.util.Base64;

/**
 * UTXO — неизрасходованный выход транзакции.
 * Задание: lessons/05-transactions.md
 */
public class TransactionOutput {

    private String id;
    private PublicKey recipientPublicKey;
    private double amount;
    private String parentTransactionId;

    public TransactionOutput(PublicKey recipientPublicKey, double amount, String parentTransactionId) {
        // TODO: Инициализируй поля и вычисли id через calculateId()
        throw new UnsupportedOperationException("Реализуй конструктор TransactionOutput");
    }

    private String calculateId() {
        // TODO: Верни sha256 от (publicKey в Base64 + amount + parentTransactionId)
        throw new UnsupportedOperationException("Реализуй calculateId");
    }

    public boolean isMine(PublicKey publicKey) {
        // TODO: Сравни publicKey с recipientPublicKey
        throw new UnsupportedOperationException("Реализуй isMine");
    }

    public String getId() {
        return id;
    }

    public PublicKey getRecipientPublicKey() {
        return recipientPublicKey;
    }

    public double getAmount() {
        return amount;
    }

    public String getParentTransactionId() {
        return parentTransactionId;
    }
}
