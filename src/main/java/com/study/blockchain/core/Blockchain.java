package com.study.blockchain.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Blockchain — цепочка блоков, связанных криптографическими hash-ами.
 *
 * Основные свойства:
 * - Каждый блок содержит hash предыдущего блока (кроме Genesis)
 * - Изменение любого блока делает невалидными все последующие
 * - Обеспечивает immutability (неизменяемость) данных
 *
 * @see Block
 * @see HashUtil
 */
public class Blockchain {

    private static final String GENESIS_DATA = "Genesis Block";

    private final List<Block> chain;

    /**
     * Создаёт новый blockchain с Genesis блоком.
     */
    public Blockchain() {
        this.chain = new ArrayList<>();
        createGenesisBlock();
    }

    /**
     * Создаёт Genesis блок и добавляет его в цепочку.
     * Genesis — первый блок, у которого previousHash = "0".
     */
    private void createGenesisBlock() {
        Block genesis = Block.createGenesisBlock(GENESIS_DATA);
        genesis.updateHash();
        chain.add(genesis);
    }

    /**
     * Добавляет новый блок с данными в цепочку.
     *
     * @param data данные для нового блока
     * @return созданный блок
     */
    public Block addBlock(String data) {
        Block lastBlock = getLatestBlock();

        int newIndex = lastBlock.getIndex() + 1;
        long timestamp = System.currentTimeMillis();
        String previousHash = lastBlock.getHash();

        Block newBlock = new Block(newIndex, timestamp, data, previousHash);
        newBlock.updateHash();

        chain.add(newBlock);
        return newBlock;
    }

    /**
     * Возвращает последний блок в цепочке.
     *
     * @return последний блок
     */
    public Block getLatestBlock() {
        return chain.get(chain.size() - 1);
    }

    /**
     * Возвращает блок по индексу.
     *
     * @param index индекс блока (0 = Genesis)
     * @return блок с указанным индексом
     * @throws IndexOutOfBoundsException если индекс вне диапазона
     */
    public Block getBlock(int index) {
        return chain.get(index);
    }

    /**
     * Возвращает неизменяемую копию цепочки блоков.
     *
     * @return список блоков (только для чтения)
     */
    public List<Block> getChain() {
        return Collections.unmodifiableList(chain);
    }

    /**
     * Возвращает количество блоков в цепочке.
     *
     * @return размер цепочки (включая Genesis)
     */
    public int size() {
        return chain.size();
    }

    /**
     * Возвращает высоту цепочки (индекс последнего блока).
     * Height = size - 1, так как Genesis имеет index = 0.
     *
     * @return высота цепочки
     */
    public int getHeight() {
        return chain.size() - 1;
    }

    /**
     * Проверяет, является ли цепочка валидной.
     *
     * Валидация проверяет:
     * 1. Hash каждого блока соответствует его данным
     * 2. previousHash каждого блока совпадает с hash предыдущего
     * 3. Genesis блок имеет корректный previousHash ("0")
     *
     * Подробнее о валидации — в уроке 4.
     *
     * @return true если цепочка валидна, false если обнаружена подделка
     */
    public boolean isValid() {
        return validateDetailed().isValid();
    }

    /**
     * Выполняет детальную валидацию цепочки.
     *
     * В отличие от isValid(), возвращает подробную информацию об ошибке:
     * - Индекс невалидного блока
     * - Описание проблемы
     *
     * @return результат валидации с деталями
     * @see ValidationResult
     */
    public ValidationResult validateDetailed() {
        // Проверяем Genesis блок
        Block genesis = chain.get(0);
        if (!genesis.getPreviousHash().equals(Block.GENESIS_PREV_HASH)) {
            return ValidationResult.failure(0,
                    "Genesis block has invalid previousHash: expected '" +
                    Block.GENESIS_PREV_HASH + "', got '" + genesis.getPreviousHash() + "'");
        }
        if (!genesis.getHash().equals(genesis.calculateHash())) {
            return ValidationResult.failure(0,
                    "Genesis block hash mismatch: data has been tampered");
        }

        // Проверяем остальные блоки
        for (int i = 1; i < chain.size(); i++) {
            Block currentBlock = chain.get(i);
            Block previousBlock = chain.get(i - 1);

            // Проверка 1: hash блока соответствует его данным
            if (!currentBlock.getHash().equals(currentBlock.calculateHash())) {
                return ValidationResult.failure(i,
                        "Block " + i + " hash mismatch: data has been tampered");
            }

            // Проверка 2: previousHash указывает на hash предыдущего блока
            if (!currentBlock.getPreviousHash().equals(previousBlock.getHash())) {
                return ValidationResult.failure(i,
                        "Block " + i + " chain break: previousHash doesn't match block " + (i - 1) + " hash");
            }
        }

        return ValidationResult.success();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Blockchain {\n");
        sb.append("  size: ").append(size()).append("\n");
        sb.append("  height: ").append(getHeight()).append("\n");
        sb.append("  valid: ").append(isValid()).append("\n");
        sb.append("  blocks:\n");
        for (Block block : chain) {
            sb.append("    ").append(block).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }
}
