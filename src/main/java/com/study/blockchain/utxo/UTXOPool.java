package com.study.blockchain.utxo;

import com.study.blockchain.transaction.TransactionOutput;
import java.security.PublicKey;
import java.util.*;

/**
 * Пул неизрасходованных выходов транзакций.
 * Задание: lessons/09-utxo.md
 */
public class UTXOPool {

    private Map<String, TransactionOutput> pool;

    public UTXOPool() {
        // TODO: Инициализируй HashMap
        throw new UnsupportedOperationException("Реализуй конструктор UTXOPool");
    }

    public void addUTXO(TransactionOutput output) {
        // TODO: Добавь output в pool по его id
        throw new UnsupportedOperationException("Реализуй addUTXO");
    }

    public void removeUTXO(String outputId) {
        // TODO: Удали UTXO из pool
        throw new UnsupportedOperationException("Реализуй removeUTXO");
    }

    public TransactionOutput getUTXO(String outputId) {
        // TODO: Верни UTXO по id
        throw new UnsupportedOperationException("Реализуй getUTXO");
    }

    public boolean contains(String outputId) {
        // TODO: Проверь наличие UTXO
        throw new UnsupportedOperationException("Реализуй contains");
    }

    public double getBalance(PublicKey owner) {
        // TODO: Сумма amount всех UTXO где isMine(owner) == true
        throw new UnsupportedOperationException("Реализуй getBalance");
    }

    public List<TransactionOutput> getUTXOsForAddress(PublicKey owner) {
        // TODO: Список всех UTXO владельца
        throw new UnsupportedOperationException("Реализуй getUTXOsForAddress");
    }

    public int size() {
        // TODO: Количество UTXO в pool
        throw new UnsupportedOperationException("Реализуй size");
    }

    public Collection<TransactionOutput> getAllUTXOs() {
        // TODO: Все UTXO
        throw new UnsupportedOperationException("Реализуй getAllUTXOs");
    }
}
