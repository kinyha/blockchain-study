package com.study.blockchain.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Blockchain — цепочка блоков, связанных криптографическими hash-ами.
 *
 * ЗАДАНИЕ (Урок 3):
 * Реализуй этот класс согласно инструкциям в lessons/03-blockchain.md
 *
 * После реализации запусти тесты:
 *   ./gradlew test --tests BlockchainTest
 */
public class Blockchain {

    private static final String GENESIS_DATA = "Genesis Block";

    // TODO (Шаг 1): Добавь поле для хранения цепочки
    // private List<Block> chain;

    /**
     * TODO (Шаг 2): Создаёт новый blockchain с Genesis блоком.
     *
     * 1. Инициализируй chain как new ArrayList<>()
     * 2. Вызови createGenesisBlock()
     */
    public Blockchain() {
        // TODO: Инициализируй chain и создай Genesis блок
        throw new UnsupportedOperationException("Реализуй конструктор Blockchain");
    }

    /**
     * TODO (Шаг 3): Создаёт Genesis блок и добавляет его в цепочку.
     */
    private void createGenesisBlock() {
        // TODO:
        // 1. Создай Genesis блок через Block.createGenesisBlock(GENESIS_DATA)
        // 2. Вызови genesis.updateHash()
        // 3. Добавь в chain
        throw new UnsupportedOperationException("Реализуй createGenesisBlock");
    }

    /**
     * TODO (Шаг 4): Добавляет новый блок с данными в цепочку.
     *
     * 1. Получи последний блок через getLatestBlock()
     * 2. Вычисли newIndex = lastBlock.getIndex() + 1
     * 3. Получи previousHash = lastBlock.getHash()
     * 4. Создай новый Block
     * 5. Вызови newBlock.updateHash()
     * 6. Добавь в chain
     * 7. Верни созданный блок
     */
    public Block addBlock(String data) {
        // TODO: Реализуй добавление блока
        throw new UnsupportedOperationException("Реализуй addBlock");
    }

    /**
     * TODO (Шаг 5): Возвращает последний блок в цепочке.
     */
    public Block getLatestBlock() {
        // TODO: return chain.get(chain.size() - 1)
        throw new UnsupportedOperationException("Реализуй getLatestBlock");
    }

    /**
     * TODO: Возвращает блок по индексу.
     */
    public Block getBlock(int index) {
        // TODO: return chain.get(index)
        throw new UnsupportedOperationException("Реализуй getBlock");
    }

    /**
     * TODO: Возвращает неизменяемую копию цепочки.
     */
    public List<Block> getChain() {
        // TODO: return Collections.unmodifiableList(chain)
        throw new UnsupportedOperationException("Реализуй getChain");
    }

    /**
     * TODO: Возвращает количество блоков в цепочке.
     */
    public int size() {
        // TODO: return chain.size()
        throw new UnsupportedOperationException("Реализуй size");
    }

    /**
     * TODO: Возвращает высоту цепочки (индекс последнего блока).
     */
    public int getHeight() {
        // TODO: return chain.size() - 1
        throw new UnsupportedOperationException("Реализуй getHeight");
    }

    /**
     * TODO (Урок 4): Проверяет валидность цепочки.
     */
    public boolean isValid() {
        // TODO: return validateDetailed().isValid()
        throw new UnsupportedOperationException("Реализуй isValid (Урок 4)");
    }

    /**
     * TODO (Урок 4): Выполняет детальную валидацию цепочки.
     *
     * Проверки:
     * 1. Genesis: previousHash == "0" и hash соответствует данным
     * 2. Остальные блоки:
     *    a) hash == calculateHash() (данные не изменены)
     *    b) previousHash == предыдущий блок.hash (цепочка не разорвана)
     *
     * @see ValidationResult
     */
    public ValidationResult validateDetailed() {
        // TODO: Реализуй валидацию согласно lessons/04-validation.md
        throw new UnsupportedOperationException("Реализуй validateDetailed (Урок 4)");
    }

    @Override
    public String toString() {
        // TODO: Верни информацию о blockchain
        throw new UnsupportedOperationException("Реализуй toString");
    }
}
