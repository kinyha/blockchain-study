package com.study.blockchain.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для класса Block.
 */
class BlockTest {

    @Test
    @DisplayName("Создание блока с корректными параметрами")
    void createBlock_withValidParams_shouldStoreAllFields() {
        // Arrange
        int index = 1;
        long timestamp = System.currentTimeMillis();
        String data = "Test transaction data";
        String previousHash = "abc123def456";

        // Act
        Block block = new Block(index, timestamp, data, previousHash);

        // Assert
        assertEquals(index, block.getIndex());
        assertEquals(timestamp, block.getTimestamp());
        assertEquals(data, block.getData());
        assertEquals(previousHash, block.getPreviousHash());
    }

    @Test
    @DisplayName("Создание Genesis блока")
    void createGenesisBlock_shouldHaveIndexZeroAndSpecialPrevHash() {
        // Act
        Block genesis = Block.createGenesisBlock("Genesis Block");

        // Assert
        assertEquals(0, genesis.getIndex());
        assertEquals(Block.GENESIS_PREV_HASH, genesis.getPreviousHash());
        assertEquals("Genesis Block", genesis.getData());
        assertTrue(genesis.getTimestamp() > 0);
    }

    @Test
    @DisplayName("Genesis блок previousHash равен '0'")
    void genesisBlock_previousHash_shouldBeZero() {
        // Act
        Block genesis = Block.createGenesisBlock("First block");

        // Assert
        assertEquals("0", genesis.getPreviousHash());
    }

    @Test
    @DisplayName("Вычисление hash блока")
    void calculateHash_shouldReturnNonEmptyHash() {
        // Arrange
        Block block = new Block(1, 1000L, "data", "prevHash");

        // Act
        String hash = block.calculateHash();

        // Assert
        assertNotNull(hash);
        assertFalse(hash.isEmpty());
        assertEquals(64, hash.length()); // SHA-256 = 64 hex символа
    }

    @Test
    @DisplayName("Одинаковые данные дают одинаковый hash")
    void calculateHash_sameData_shouldReturnSameHash() {
        // Arrange
        Block block1 = new Block(1, 1000L, "data", "prevHash");
        Block block2 = new Block(1, 1000L, "data", "prevHash");

        // Act & Assert
        assertEquals(block1.calculateHash(), block2.calculateHash());
    }

    @Test
    @DisplayName("Разные данные дают разный hash")
    void calculateHash_differentData_shouldReturnDifferentHash() {
        // Arrange
        Block block1 = new Block(1, 1000L, "data1", "prevHash");
        Block block2 = new Block(1, 1000L, "data2", "prevHash");

        // Act & Assert
        assertNotEquals(block1.calculateHash(), block2.calculateHash());
    }

    @Test
    @DisplayName("updateHash() устанавливает hash блока")
    void updateHash_shouldSetHashField() {
        // Arrange
        Block block = new Block(1, 1000L, "data", "prevHash");
        assertTrue(block.getHash().isEmpty());

        // Act
        block.updateHash();

        // Assert
        assertFalse(block.getHash().isEmpty());
        assertEquals(block.calculateHash(), block.getHash());
    }

    @Test
    @DisplayName("toString() содержит все поля")
    void toString_shouldContainAllFields() {
        // Arrange
        Block block = new Block(5, 123456789L, "test data", "abc123");
        block.updateHash();

        // Act
        String str = block.toString();

        // Assert
        assertTrue(str.contains("index=5"));
        assertTrue(str.contains("timestamp=123456789"));
        assertTrue(str.contains("test data"));
        assertTrue(str.contains("abc123"));
    }
}
