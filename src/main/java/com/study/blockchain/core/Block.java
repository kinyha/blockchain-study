package com.study.blockchain.core;

/**
 * Block — базовая единица данных в blockchain.
 *
 * ЗАДАНИЕ (Урок 1):
 * Реализуй этот класс согласно инструкциям в lessons/01-block.md
 *
 * После реализации запусти тесты:
 *   ./gradlew test --tests BlockTest
 */
public class Block {

    /**
     * Значение previousHash для Genesis блока.
     */
    public static final String GENESIS_PREV_HASH = "0";

    private final int index;
    private final long timestamp;
    private String data;
    private String previousHash;
    private String hash;


    public Block(int index, long timestamp, String data, String previousHash) {
        this.index = index;
        this.timestamp = timestamp;
        this.data = data;
        this.previousHash = previousHash;
        this.hash = "";
    }

    public static Block createGenesisBlock(String data) {
        return new Block(0, System.currentTimeMillis(),data,GENESIS_PREV_HASH);
    }

    public String calculateHash() {
        String input = index + String.valueOf(timestamp) + data + previousHash;
        return HashUtil.sha256(input);
    }

    /**
     * Пересчитывает и устанавливает hash блока.
     */
    public void updateHash() {
        this.hash = calculateHash();
    }

    public int getIndex() {
        return this.index;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public String getData() {
        return this.data;
    }

    public String getPreviousHash() {
        return this.previousHash;
    }

    public String getHash() {
        return this.hash;
    }

    /**
     * Устанавливает hash блока.
     */
    public void setHash(String hash) {
        this.hash = hash;
    }

    // Для тестирования атак (Урок 4)
    void setDataForTesting(String data) {
        this.data = data;
    }

    void setPreviousHashForTesting(String previousHash) {
        this.previousHash = previousHash;
    }

    @Override
    public String toString() {
        return "Block{" +
                "index=" + index +
                ", timestamp=" + timestamp +
                ", data='" + data + '\'' +
                ", previousHash='" + previousHash + '\'' +
                ", hash='" + hash + '\'' +
                '}';
    }
}
