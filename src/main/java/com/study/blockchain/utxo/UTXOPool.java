package com.study.blockchain.utxo;

import com.study.blockchain.transaction.TransactionOutput;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UTXOPool {

    private Map<String, TransactionOutput> pool;
    private Set<String> reserved;

    public UTXOPool() {
        this.pool = new ConcurrentHashMap<>();
        this.reserved = ConcurrentHashMap.newKeySet();
    }

    public void addUTXO(TransactionOutput output) {
        pool.put(output.getId(), output);
    }

    public void removeUTXO(String outputId) {
        pool.remove(outputId);
        reserved.remove(outputId);
    }

    public void reserveUTXO(String outputId) {
        reserved.add(outputId);
    }

    public void releaseReservation(String outputId) {
        reserved.remove(outputId);
    }

    public boolean isReserved(String outputId) {
        return reserved.contains(outputId);
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
            if (utxo.isMine(owner) && !reserved.contains(utxo.getId())) {
                result.add(utxo);
            }
        }
        return result;
    }

    public double getAvailableBalance(PublicKey owner) {
        double balance = 0;
        for (TransactionOutput utxo : pool.values()) {
            if (utxo.isMine(owner) && !reserved.contains(utxo.getId())) {
                balance += utxo.getAmount();
            }
        }
        return balance;
    }

    public int size() {
        return pool.size();
    }

    public Collection<TransactionOutput> getAllUTXOs() {
        return pool.values();
    }
}
