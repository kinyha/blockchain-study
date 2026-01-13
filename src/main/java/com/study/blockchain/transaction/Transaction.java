package com.study.blockchain.transaction;

import com.study.blockchain.core.HashUtil;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Транзакция — передача ценности через inputs и outputs.
 * Задание: lessons/05-transactions.md
 */
public class Transaction {

    private String transactionId;
    private List<TransactionInput> inputs;
    private List<TransactionOutput> outputs;
    private long timestamp;

    public Transaction() {
        this.inputs = new ArrayList<>();
        this.outputs = new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
    }

    public String calculateHash() {
        StringBuilder sb = new StringBuilder();
        sb.append(timestamp);

        for (TransactionInput input : inputs) {
            sb.append(input.getTransactionOutputId());
        }

        for (TransactionOutput output : outputs) {
            String keyEncoded = Base64.getEncoder().encodeToString(
                    output.getRecipientPublicKey().getEncoded());
            sb.append(keyEncoded);
            sb.append(output.getAmount());
        }
        return HashUtil.sha256(sb.toString());
    }

    public void finalizeTransaction() {
        this.transactionId = calculateHash();
        for (int i = 0; i < outputs.size(); i++) {
            TransactionOutput oldOutput = outputs.get(i);
            TransactionOutput newOutput = new TransactionOutput(
                    oldOutput.getRecipientPublicKey(),
                    oldOutput.getAmount(),
                    this.transactionId
            );
            outputs.set(i, newOutput);
        }
    }

    public double getInputsValue() {
        double total = 0;
        for (TransactionInput input : inputs) {
            if (input.getUTXO() != null) {
                total += input.getUTXO().getAmount();
            }
        }
        return total;
    }

    public double getOutputsValue() {
        double total = 0;
        for (TransactionOutput output : outputs) {
            total += output.getAmount();
        }
        return total;
    }

    public void addInput(TransactionInput input) {
        inputs.add(input);
    }

    public void addOutput(TransactionOutput output) {
        outputs.add(output);
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public List<TransactionInput> getInputs() {
        return inputs;
    }

    public List<TransactionOutput> getOutputs() {
        return outputs;
    }

    public long getTimestamp() {
        return timestamp;
    }

    // Урок 7: Подпись транзакций
    public void signAllInputs(PrivateKey privateKey) {
        for (TransactionInput input : inputs) {
            input.sign(privateKey);
        }
    }

    public boolean verifySignatures() {
        for (TransactionInput input : inputs) {
            if (input.getUTXO() == null) {
                return false;
            }
            PublicKey publicKey = input.getUTXO().getRecipientPublicKey();
            if (!input.verifySignature(publicKey)) {
                return false;
            }
        }
        return true;
    }
}
