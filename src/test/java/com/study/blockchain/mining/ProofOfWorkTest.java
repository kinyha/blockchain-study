package com.study.blockchain.mining;

import com.study.blockchain.core.Block;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProofOfWorkTest {

    @Nested
    @DisplayName("Конструктор и getTarget")
    class TargetTests {

        @Test
        @DisplayName("создаётся с заданной сложностью")
        void createsWithDifficulty() {
            ProofOfWork pow = new ProofOfWork(4);

            assertEquals(4, pow.getDifficulty());
        }

        @Test
        @DisplayName("getTarget возвращает строку нулей")
        void targetIsZeros() {
            ProofOfWork pow = new ProofOfWork(4);

            assertEquals("0000", pow.getTarget());
        }

        @Test
        @DisplayName("getTarget для разных сложностей")
        void targetForDifferentDifficulties() {
            assertEquals("0", new ProofOfWork(1).getTarget());
            assertEquals("00", new ProofOfWork(2).getTarget());
            assertEquals("000", new ProofOfWork(3).getTarget());
            assertEquals("00000", new ProofOfWork(5).getTarget());
        }
    }

    @Nested
    @DisplayName("Mining")
    class MiningTests {

        @Test
        @DisplayName("майнит блок до валидного hash")
        void minesBlock() {

            Block block = new Block(1, System.currentTimeMillis(), "Test data", "0");
            ProofOfWork pow = new ProofOfWork(4);

            long start = System.currentTimeMillis();
            pow.mine(block);
            long elapsed = System.currentTimeMillis() - start;

            System.out.println("Mining time: " + elapsed + "ms, nonce=" + block.getNonce() + ", hash=" + block.getHash());

            assertTrue(block.getHash().startsWith("00"));
        }

        @Test
        @DisplayName("nonce увеличивается при майнинге")
        void nonceIncreases() {
            Block block = new Block(1, System.currentTimeMillis(), "Test", "prev");
            ProofOfWork pow = new ProofOfWork(3);

            pow.mine(block);

            assertTrue(block.getNonce() > 0);
        }

        @Test
        @DisplayName("майнинг со сложностью 4")
        void miningDifficulty4() {
            Block block = new Block(1, System.currentTimeMillis(), "Mining test", "0");
            ProofOfWork pow = new ProofOfWork(4);

            pow.mine(block);

            assertTrue(block.getHash().startsWith("0000"));
        }

        @Test
        @DisplayName("разные данные требуют разный nonce")
        void differentDataDifferentNonce() {
            Block block1 = new Block(1, 1000L, "Data A", "0");
            Block block2 = new Block(1, 1000L, "Data B", "0");
            ProofOfWork pow = new ProofOfWork(3);

            pow.mine(block1);
            pow.mine(block2);

            // Очень маловероятно что nonce совпадут
            assertNotEquals(block1.getNonce(), block2.getNonce());
        }
    }

    @Nested
    @DisplayName("Валидация proof")
    class ValidationTests {

        @Test
        @DisplayName("isValidProof для намайненного блока")
        void validProofForMinedBlock() {
            Block block = new Block(1, System.currentTimeMillis(), "Test", "prev");
            ProofOfWork pow = new ProofOfWork(3);

            pow.mine(block);

            assertTrue(pow.isValidProof(block));
        }

        @Test
        @DisplayName("isValidProof false для ненамайненного блока")
        void invalidProofForUnminedBlock() {
            Block block = new Block(1, System.currentTimeMillis(), "Test", "prev");
            block.updateHash();  // Hash без майнинга

            ProofOfWork pow = new ProofOfWork(4);

            assertFalse(pow.isValidProof(block));
        }

        @Test
        @DisplayName("proof валиден для меньшей сложности")
        void proofValidForLowerDifficulty() {
            Block block = new Block(1, System.currentTimeMillis(), "Test", "prev");
            ProofOfWork pow4 = new ProofOfWork(4);
            ProofOfWork pow2 = new ProofOfWork(2);

            pow4.mine(block);

            // Блок намайнен для difficulty 4, значит подходит и для 2
            assertTrue(pow2.isValidProof(block));
        }

        @Test
        @DisplayName("proof невалиден для большей сложности")
        void proofInvalidForHigherDifficulty() {
            Block block = new Block(1, System.currentTimeMillis(), "Test", "prev");
            ProofOfWork pow2 = new ProofOfWork(2);
            ProofOfWork pow4 = new ProofOfWork(4);

            pow2.mine(block);

            // Блок намайнен для difficulty 2, может не подойти для 4
            // (Статистически почти наверняка не подойдёт)
            if (!block.getHash().startsWith("0000")) {
                assertFalse(pow4.isValidProof(block));
            }
        }
    }

    @Nested
    @DisplayName("Демо скорости")
    class PerformanceDemo {

        @Test
        @DisplayName("difficulty 2 быстрый")
        void difficulty2Fast() {
            Block block = new Block(1, System.currentTimeMillis(), "Speed test", "0");
            ProofOfWork pow = new ProofOfWork(2);

            long start = System.currentTimeMillis();
            pow.mine(block);
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(elapsed < 1000, "Difficulty 2 должен быть < 1 сек");
            System.out.println("Difficulty 2: " + elapsed + "ms, nonce=" + block.getNonce());
        }

        @Test
        @DisplayName("difficulty 4 умеренный")
        void difficulty4Moderate() {
            Block block = new Block(1, System.currentTimeMillis(), "Speed test", "0");
            ProofOfWork pow = new ProofOfWork(4);

            long start = System.currentTimeMillis();
            pow.mine(block);
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(elapsed < 10000, "Difficulty 4 должен быть < 10 сек");
            System.out.println("Difficulty 4: " + elapsed + "ms, nonce=" + block.getNonce());
        }
    }

    @Nested
    @DisplayName("Genesis блок с PoW")
    class GenesisWithPoW {

        @Test
        @DisplayName("Genesis блок можно намайнить")
        void mineGenesisBlock() {
            Block genesis = Block.createGenesisBlock("Genesis with PoW");
            ProofOfWork pow = new ProofOfWork(3);

            pow.mine(genesis);

            assertTrue(genesis.getHash().startsWith("000"));
            assertEquals(0, genesis.getIndex());
        }
    }
}
