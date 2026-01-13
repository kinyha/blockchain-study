package com.study.blockchain.utxo;

import com.study.blockchain.transaction.TransactionOutput;
import java.security.PublicKey;
import java.util.*;

public class UTXOPool {

    private Map<String, TransactionOutput> pool;

    public UTXOPool() {
        this.pool = new HashMap<>();
    }

    public void addUTXO(TransactionOutput output) {
        pool.put(output.getId(), output);
    }

    public void removeUTXO(String outputId) {
        pool.remove(outputId);
    }

    public TransactionOutput getUTXO(String outputId) {
        return pool.get(outputId);
    }

    public boolean contains(String outputId) {
        return pool.containsKey(outputId);
    }

    public double getBalance(PublicKey owner) {
        double balance = 0;
        for (TransactionOutput utxo : pool.values()) {
            if (utxo.isMine(owner)) {
                balance += utxo.getAmount();
            }
        }
        return balance;
    }

    public List<TransactionOutput> getUTXOsForAddress(PublicKey owner) {
        List<TransactionOutput> result = new ArrayList<>();
        for (TransactionOutput utxo : pool.values()) {
            if (utxo.isMine(owner)) {
                result.add(utxo);
            }
        }
        return result;
    }

    public int size() {
        return pool.size();
    }

    public Collection<TransactionOutput> getAllUTXOs() {
        return pool.values();
    }
}
