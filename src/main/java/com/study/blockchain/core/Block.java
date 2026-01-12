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

    // TODO (Шаг 1): Добавь приватные поля
    // - index: int — порядковый номер блока
    // - timestamp: long — время создания (миллисекунды)
    // - data: String — данные блока
    // - previousHash: String — hash предыдущего блока
    // - hash: String — hash текущего блока

    /**
     * TODO (Шаг 2): Реализуй конструктор
     *
     * @param index        порядковый номер блока (0 для Genesis)
     * @param timestamp    время создания в миллисекундах
     * @param data         данные блока
     * @param previousHash hash предыдущего блока ("0" для Genesis)
     */
    public Block(int index, long timestamp, String data, String previousHash) {
        // TODO: Инициализируй все поля
        // TODO: hash изначально пустая строка ""
        throw new UnsupportedOperationException("Реализуй конструктор Block");
    }

    /**
     * TODO (Шаг 3): Создай статический метод для Genesis блока
     *
     * Genesis блок:
     * - index = 0
     * - timestamp = текущее время (System.currentTimeMillis())
     * - previousHash = GENESIS_PREV_HASH ("0")
     */
    public static Block createGenesisBlock(String data) {
        // TODO: Верни новый Block с параметрами Genesis
        throw new UnsupportedOperationException("Реализуй createGenesisBlock");
    }

    /**
     * TODO (Шаг 4 — после урока 2): Вычисляет hash блока
     *
     * Hash = SHA256(index + timestamp + data + previousHash)
     * Используй HashUtil.sha256()
     */
    public String calculateHash() {
        // TODO: Собери строку из всех полей и верни её hash
        throw new UnsupportedOperationException("Реализуй calculateHash (Урок 2)");
    }

    /**
     * Пересчитывает и устанавливает hash блока.
     */
    public void updateHash() {
        // TODO: this.hash = calculateHash()
        throw new UnsupportedOperationException("Реализуй updateHash");
    }

    // TODO (Шаг 5): Добавь геттеры для всех полей
    // getIndex(), getTimestamp(), getData(), getPreviousHash(), getHash()

    public int getIndex() {
        throw new UnsupportedOperationException("Реализуй getIndex");
    }

    public long getTimestamp() {
        throw new UnsupportedOperationException("Реализуй getTimestamp");
    }

    public String getData() {
        throw new UnsupportedOperationException("Реализуй getData");
    }

    public String getPreviousHash() {
        throw new UnsupportedOperationException("Реализуй getPreviousHash");
    }

    public String getHash() {
        throw new UnsupportedOperationException("Реализуй getHash");
    }

    /**
     * Устанавливает hash блока.
     */
    public void setHash(String hash) {
        // TODO: this.hash = hash
        throw new UnsupportedOperationException("Реализуй setHash");
    }

    // Для тестирования атак (Урок 4)
    void setDataForTesting(String data) {
        // TODO: this.data = data
        throw new UnsupportedOperationException("Реализуй setDataForTesting");
    }

    void setPreviousHashForTesting(String previousHash) {
        // TODO: this.previousHash = previousHash
        throw new UnsupportedOperationException("Реализуй setPreviousHashForTesting");
    }

    @Override
    public String toString() {
        // TODO (Шаг 6): Верни строку вида:
        // "Block{index=0, timestamp=123, data='...', previousHash='...', hash='...'}"
        throw new UnsupportedOperationException("Реализуй toString");
    }
}
