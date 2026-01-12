package com.study.blockchain.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты валидации blockchain.
 * Демонстрируют обнаружение различных видов подделки.
 */
class ValidationTest {

    private Blockchain blockchain;

    @BeforeEach
    void setUp() {
        blockchain = new Blockchain();
        blockchain.addBlock("Transaction 1");
        blockchain.addBlock("Transaction 2");
        blockchain.addBlock("Transaction 3");
    }

    // ==================== Валидная цепочка ====================

    @Nested
    @DisplayName("Валидная цепочка")
    class ValidChainTests {

        @Test
        @DisplayName("Новая цепочка валидна")
        void newBlockchain_shouldBeValid() {
            Blockchain fresh = new Blockchain();
            assertTrue(fresh.isValid());
        }

        @Test
        @DisplayName("Цепочка с блоками валидна")
        void blockchainWithBlocks_shouldBeValid() {
            assertTrue(blockchain.isValid());
        }

        @Test
        @DisplayName("validateDetailed() возвращает success для валидной цепочки")
        void validateDetailed_validChain_shouldReturnSuccess() {
            ValidationResult result = blockchain.validateDetailed();

            assertTrue(result.isValid());
            assertEquals(-1, result.getInvalidBlockIndex());
            assertNull(result.getErrorMessage());
        }
    }

    // ==================== Подделка данных ====================

    @Nested
    @DisplayName("Атака: изменение данных блока")
    class DataTamperingTests {

        @Test
        @DisplayName("Изменение данных блока делает цепочку невалидной")
        void tamperBlockData_shouldInvalidateChain() {
            // Изменяем данные Block 1 (имитируем подделку)
            Block block1 = blockchain.getBlock(1);
            block1.setDataForTesting("HACKED: Alice→Hacker: 1000000");

            assertFalse(blockchain.isValid());
        }

        @Test
        @DisplayName("validateDetailed() показывает индекс подделанного блока")
        void tamperBlockData_validateDetailed_shouldShowTamperedBlockIndex() {
            Block block2 = blockchain.getBlock(2);
            block2.setDataForTesting("Tampered data");

            ValidationResult result = blockchain.validateDetailed();

            assertFalse(result.isValid());
            assertEquals(2, result.getInvalidBlockIndex());
            assertTrue(result.getErrorMessage().contains("hash mismatch"));
        }

        @Test
        @DisplayName("Изменение данных Genesis блока обнаруживается")
        void tamperGenesisData_shouldBeDetected() {
            Block genesis = blockchain.getBlock(0);
            genesis.setDataForTesting("Fake Genesis");

            ValidationResult result = blockchain.validateDetailed();

            assertFalse(result.isValid());
            assertEquals(0, result.getInvalidBlockIndex());
        }
    }

    // ==================== Подделка hash ====================

    @Nested
    @DisplayName("Атака: подмена hash блока")
    class HashTamperingTests {

        @Test
        @DisplayName("Изменение hash блока делает цепочку невалидной")
        void tamperBlockHash_shouldInvalidateChain() {
            Block block1 = blockchain.getBlock(1);
            block1.setHash("fake_hash_12345");

            assertFalse(blockchain.isValid());
        }

        @Test
        @DisplayName("Пустой hash обнаруживается")
        void emptyHash_shouldBeDetected() {
            Block block1 = blockchain.getBlock(1);
            block1.setHash("");

            assertFalse(blockchain.isValid());
        }
    }

    // ==================== Разрыв цепочки ====================

    @Nested
    @DisplayName("Атака: разрыв цепочки (previousHash)")
    class ChainBreakTests {

        @Test
        @DisplayName("Изменение previousHash разрывает цепочку")
        void tamperPreviousHash_shouldBreakChain() {
            Block block2 = blockchain.getBlock(2);
            block2.setPreviousHashForTesting("wrong_previous_hash");
            // Пересчитываем hash, чтобы пройти первую проверку
            block2.updateHash();

            ValidationResult result = blockchain.validateDetailed();

            assertFalse(result.isValid());
            assertEquals(2, result.getInvalidBlockIndex());
            assertTrue(result.getErrorMessage().contains("chain break"));
        }

        @Test
        @DisplayName("Изменение previousHash Genesis обнаруживается")
        void tamperGenesisPreviousHash_shouldBeDetected() {
            Block genesis = blockchain.getBlock(0);
            genesis.setPreviousHashForTesting("should_be_zero");
            genesis.updateHash();

            ValidationResult result = blockchain.validateDetailed();

            assertFalse(result.isValid());
            assertEquals(0, result.getInvalidBlockIndex());
            assertTrue(result.getErrorMessage().contains("previousHash"));
        }
    }

    // ==================== Каскадная подделка ====================

    @Nested
    @DisplayName("Атака: каскадный пересчёт hash-ей")
    class CascadeAttackTests {

        @Test
        @DisplayName("Каскадный пересчёт без изменения previousHash всё равно обнаруживается")
        void cascadeRecalculateWithoutFixingLinks_shouldBeDetected() {
            // Изменяем данные Block 1
            Block block1 = blockchain.getBlock(1);
            block1.setDataForTesting("Hacked data");
            block1.updateHash();  // Пересчитываем hash

            // Block 2 всё ещё ссылается на старый hash Block 1
            // → цепочка разорвана

            ValidationResult result = blockchain.validateDetailed();

            assertFalse(result.isValid());
            assertEquals(2, result.getInvalidBlockIndex());
            assertTrue(result.getErrorMessage().contains("chain break"));
        }

        @Test
        @DisplayName("Полный каскадный пересчёт делает цепочку валидной (без PoW)")
        void fullCascadeRecalculate_shouldMakeChainValid() {
            // Это демонстрирует, почему нужен Proof-of-Work!
            // Без PoW злоумышленник может пересчитать всю цепочку

            // Изменяем Block 1
            Block block1 = blockchain.getBlock(1);
            block1.setDataForTesting("Hacked data");
            block1.updateHash();

            // Каскадно обновляем все последующие блоки
            for (int i = 2; i < blockchain.size(); i++) {
                Block current = blockchain.getBlock(i);
                Block previous = blockchain.getBlock(i - 1);
                current.setPreviousHashForTesting(previous.getHash());
                current.updateHash();
            }

            // Теперь цепочка формально валидна!
            // Вот почему нужен Proof-of-Work (урок 8)
            assertTrue(blockchain.isValid());
        }
    }

    // ==================== ValidationResult ====================

    @Nested
    @DisplayName("ValidationResult")
    class ValidationResultTests {

        @Test
        @DisplayName("success() создаёт валидный результат")
        void success_shouldCreateValidResult() {
            ValidationResult result = ValidationResult.success();

            assertTrue(result.isValid());
            assertEquals(-1, result.getInvalidBlockIndex());
            assertNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("failure() создаёт невалидный результат с деталями")
        void failure_shouldCreateInvalidResultWithDetails() {
            ValidationResult result = ValidationResult.failure(5, "Test error");

            assertFalse(result.isValid());
            assertEquals(5, result.getInvalidBlockIndex());
            assertEquals("Test error", result.getErrorMessage());
        }

        @Test
        @DisplayName("toString() содержит информацию об ошибке")
        void toString_failure_shouldContainErrorInfo() {
            ValidationResult result = ValidationResult.failure(3, "Hash mismatch");

            String str = result.toString();

            assertTrue(str.contains("valid=false"));
            assertTrue(str.contains("3"));
            assertTrue(str.contains("Hash mismatch"));
        }
    }
}
