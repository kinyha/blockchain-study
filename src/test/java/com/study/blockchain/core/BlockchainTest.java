package com.study.blockchain.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для класса Blockchain.
 */
class BlockchainTest {

    private Blockchain blockchain;

    @BeforeEach
    void setUp() {
        blockchain = new Blockchain();
    }

    // ==================== Создание ====================

    @Test
    @DisplayName("Новый blockchain содержит Genesis блок")
    void newBlockchain_shouldContainGenesisBlock() {
        assertEquals(1, blockchain.size());
        assertEquals(0, blockchain.getBlock(0).getIndex());
    }

    @Test
    @DisplayName("Genesis блок имеет previousHash = '0'")
    void genesisBlock_shouldHaveZeroPreviousHash() {
        Block genesis = blockchain.getBlock(0);

        assertEquals(Block.GENESIS_PREV_HASH, genesis.getPreviousHash());
    }

    @Test
    @DisplayName("Genesis блок имеет вычисленный hash")
    void genesisBlock_shouldHaveCalculatedHash() {
        Block genesis = blockchain.getBlock(0);

        assertNotNull(genesis.getHash());
        assertFalse(genesis.getHash().isEmpty());
        assertEquals(64, genesis.getHash().length());
    }

    // ==================== Добавление блоков ====================

    @Test
    @DisplayName("Добавление блока увеличивает размер цепочки")
    void addBlock_shouldIncreaseSizeByOne() {
        int initialSize = blockchain.size();

        blockchain.addBlock("Test data");

        assertEquals(initialSize + 1, blockchain.size());
    }

    @Test
    @DisplayName("Новый блок имеет корректный index")
    void addBlock_shouldHaveCorrectIndex() {
        blockchain.addBlock("Block 1");
        blockchain.addBlock("Block 2");

        assertEquals(1, blockchain.getBlock(1).getIndex());
        assertEquals(2, blockchain.getBlock(2).getIndex());
    }

    @Test
    @DisplayName("Новый блок содержит переданные данные")
    void addBlock_shouldContainProvidedData() {
        String testData = "Important transaction data";

        blockchain.addBlock(testData);

        assertEquals(testData, blockchain.getLatestBlock().getData());
    }

    @Test
    @DisplayName("Новый блок ссылается на предыдущий через previousHash")
    void addBlock_shouldReferToPreviousBlockHash() {
        Block firstBlock = blockchain.getLatestBlock();
        blockchain.addBlock("New block");
        Block secondBlock = blockchain.getLatestBlock();

        assertEquals(firstBlock.getHash(), secondBlock.getPreviousHash());
    }

    @Test
    @DisplayName("Цепочка блоков связана через hash-и")
    void blockchain_shouldBeLinkedThroughHashes() {
        blockchain.addBlock("Block 1");
        blockchain.addBlock("Block 2");
        blockchain.addBlock("Block 3");

        for (int i = 1; i < blockchain.size(); i++) {
            Block current = blockchain.getBlock(i);
            Block previous = blockchain.getBlock(i - 1);

            assertEquals(previous.getHash(), current.getPreviousHash(),
                    "Block " + i + " should reference hash of block " + (i - 1));
        }
    }

    // ==================== Получение блоков ====================

    @Test
    @DisplayName("getLatestBlock возвращает последний добавленный блок")
    void getLatestBlock_shouldReturnMostRecentBlock() {
        blockchain.addBlock("First");
        blockchain.addBlock("Second");
        Block latest = blockchain.addBlock("Third");

        assertSame(latest, blockchain.getLatestBlock());
        assertEquals("Third", blockchain.getLatestBlock().getData());
    }

    @Test
    @DisplayName("getBlock возвращает блок по индексу")
    void getBlock_shouldReturnBlockByIndex() {
        blockchain.addBlock("Block 1");
        blockchain.addBlock("Block 2");

        assertEquals("Block 1", blockchain.getBlock(1).getData());
        assertEquals("Block 2", blockchain.getBlock(2).getData());
    }

    @Test
    @DisplayName("getBlock выбрасывает исключение для невалидного индекса")
    void getBlock_invalidIndex_shouldThrowException() {
        assertThrows(IndexOutOfBoundsException.class, () -> blockchain.getBlock(999));
    }

    // ==================== Размер и высота ====================

    @Test
    @DisplayName("size возвращает количество блоков")
    void size_shouldReturnNumberOfBlocks() {
        assertEquals(1, blockchain.size()); // Genesis

        blockchain.addBlock("1");
        blockchain.addBlock("2");

        assertEquals(3, blockchain.size());
    }

    @Test
    @DisplayName("getHeight возвращает индекс последнего блока")
    void getHeight_shouldReturnLatestBlockIndex() {
        assertEquals(0, blockchain.getHeight()); // Только Genesis

        blockchain.addBlock("1");
        assertEquals(1, blockchain.getHeight());

        blockchain.addBlock("2");
        assertEquals(2, blockchain.getHeight());
    }

    // ==================== Неизменяемость ====================

    @Test
    @DisplayName("getChain возвращает неизменяемый список")
    void getChain_shouldReturnUnmodifiableList() {
        assertThrows(UnsupportedOperationException.class, () -> {
            blockchain.getChain().add(new Block(999, 0, "hack", "hack"));
        });
    }

    // ==================== Валидация (базовая, подробнее в уроке 4) ====================

    @Test
    @DisplayName("Новый blockchain валиден")
    void newBlockchain_shouldBeValid() {
        assertTrue(blockchain.isValid());
    }

    @Test
    @DisplayName("Blockchain после добавления блоков остаётся валидным")
    void blockchainWithBlocks_shouldBeValid() {
        blockchain.addBlock("Block 1");
        blockchain.addBlock("Block 2");
        blockchain.addBlock("Block 3");

        assertTrue(blockchain.isValid());
    }
}
