package com.study.blockchain.transaction;

import com.study.blockchain.core.HashUtil;
import java.security.PublicKey;
import java.util.Base64;

public class TransactionOutput {

    private String id;
    private PublicKey recipientPublicKey;
    private double amount;
    private String parentTransactionId;

    public TransactionOutput(PublicKey recipientPublicKey, double amount, String parentTransactionId) {
        this.recipientPublicKey = recipientPublicKey;
        this.amount = amount;
        this.parentTransactionId = parentTransactionId;
        this.id = calculateId();
    }

    private String calculateId() {
        String publicKeyEncoded = Base64.getEncoder().encodeToString(recipientPublicKey.getEncoded());
        return HashUtil.sha256(publicKeyEncoded + amount + parentTransactionId);
    }

    public boolean isMine(PublicKey publicKey) {
        return recipientPublicKey.equals(publicKey);
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
