package com.study.blockchain.transaction;

import com.study.blockchain.core.HashUtil;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

/**
 * Транзакция — передача ценности через inputs и outputs.
 * Задание: lessons/05-transactions.md
 */
public class Transaction {

    private String transactionId;
    private List<TransactionInput> inputs;
    private List<TransactionOutput> outputs;

    public Transaction() {
        // TODO: Инициализируй списки inputs и outputs
        throw new UnsupportedOperationException("Реализуй конструктор Transaction");
    }

    public String calculateHash() {
        // TODO: Собери данные из inputs (outputId) и outputs (recipientKey + amount)
        // Верни sha256 от собранной строки
        throw new UnsupportedOperationException("Реализуй calculateHash");
    }

    public void finalizeTransaction() {
        // TODO: Вычисли и установи transactionId
        // Установи parentTransactionId для каждого output
        throw new UnsupportedOperationException("Реализуй finalizeTransaction");
    }

    public double getInputsValue() {
        // TODO: Сумма amount всех UTXO из inputs
        throw new UnsupportedOperationException("Реализуй getInputsValue");
    }

    public double getOutputsValue() {
        // TODO: Сумма amount всех outputs
        throw new UnsupportedOperationException("Реализуй getOutputsValue");
    }

    public void addInput(TransactionInput input) {
        // TODO: Добавь input в список
        throw new UnsupportedOperationException("Реализуй addInput");
    }

    public void addOutput(TransactionOutput output) {
        // TODO: Добавь output в список
        throw new UnsupportedOperationException("Реализуй addOutput");
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

    // Урок 7: Подпись транзакций
    public void signAllInputs(PrivateKey privateKey) {
        // TODO: Подпиши все inputs приватным ключом
        throw new UnsupportedOperationException("Реализуй signAllInputs (Урок 7)");
    }

    public boolean verifySignatures() {
        // TODO: Проверь подписи всех inputs
        // Для каждого input: publicKey = input.getUTXO().getRecipientPublicKey()
        throw new UnsupportedOperationException("Реализуй verifySignatures (Урок 7)");
    }
}
