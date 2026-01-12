package com.study.blockchain.core;

/**
 * Block — базовая единица данных в blockchain.
 *
 * Каждый блок содержит:
 * - index: порядковый номер в цепочке
 * - timestamp: время создания (Unix time в миллисекундах)
 * - data: полезная нагрузка (в будущем — список транзакций)
 * - previousHash: hash предыдущего блока (связывает блоки в цепочку)
 * - hash: криптографический отпечаток текущего блока
 *
 * @see Blockchain
 * @see HashUtil
 */
public class Block {

    /**
     * Значение previousHash для Genesis блока.
     * Genesis — первый блок в цепочке, у него нет предшественника.
     */
    public static final String GENESIS_PREV_HASH = "0";

    private final int index;
    private final long timestamp;
    private String data;
    private String previousHash;
    private String hash;

    /**
     * Создаёт новый блок.
     *
     * @param index        порядковый номер блока (0 для Genesis)
     * @param timestamp    время создания в миллисекундах
     * @param data         данные блока
     * @param previousHash hash предыдущего блока ("0" для Genesis)
     */
    public Block(int index, long timestamp, String data, String previousHash) {
        this.index = index;
        this.timestamp = timestamp;
        this.data = data;
        this.previousHash = previousHash;
        // hash будет вычислен позже через calculateHash() или установлен вручную
        this.hash = "";
    }

    /**
     * Создаёт Genesis блок (первый блок в цепочке).
     *
     * Genesis блок имеет:
     * - index = 0
     * - previousHash = "0"
     * - timestamp = текущее время
     *
     * @param data данные Genesis блока
     * @return новый Genesis блок
     */
    public static Block createGenesisBlock(String data) {
        return new Block(0, System.currentTimeMillis(), data, GENESIS_PREV_HASH);
    }

    /**
     * Вычисляет hash блока на основе его содержимого.
     *
     * Hash вычисляется из: index + timestamp + data + previousHash + nonce (в будущем).
     * Любое изменение данных приводит к изменению hash.
     *
     * @return SHA-256 hash блока
     */
    public String calculateHash() {
        // Реализация будет добавлена в уроке 2 (Hashing)
        // Пока возвращаем заглушку
        String input = index + timestamp + data + previousHash;
        return HashUtil.sha256(input);
    }

    /**
     * Пересчитывает и устанавливает hash блока.
     * Вызывается после создания блока или изменения данных.
     */
    public void updateHash() {
        this.hash = calculateHash();
    }

    // ==================== Getters ====================

    public int getIndex() {
        return index;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getData() {
        return data;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getHash() {
        return hash;
    }

    /**
     * Устанавливает hash блока.
     * Используется при загрузке блока из хранилища или при mining.
     *
     * @param hash вычисленный hash
     */
    public void setHash(String hash) {
        this.hash = hash;
    }

    // ==================== Package-private методы для тестирования ====================

    /**
     * Устанавливает данные блока.
     * ТОЛЬКО ДЛЯ ТЕСТИРОВАНИЯ! Имитирует подделку данных.
     */
    void setDataForTesting(String data) {
        this.data = data;
    }

    /**
     * Устанавливает previousHash блока.
     * ТОЛЬКО ДЛЯ ТЕСТИРОВАНИЯ! Имитирует разрыв цепочки.
     */
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
