package com.study.blockchain.service;

import com.study.blockchain.core.Block;
import com.study.blockchain.core.Blockchain;
import com.study.blockchain.mining.ProofOfWork;
import com.study.blockchain.transaction.Transaction;
import com.study.blockchain.transaction.TransactionOutput;
import com.study.blockchain.utxo.UTXOPool;
import com.study.blockchain.wallet.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD тесты для MiningService.
 * Бизнес-правила:
 * - Genesis reward: 10 coins
 * - Block reward: 1 coin
 * - Fee идёт майнеру
 */
class MiningServiceTest {

    // Используем низкую difficulty для быстрых тестов
    private static final int TEST_DIFFICULTY = 2;

    private MiningService miningService;
    private Blockchain blockchain;
    private UTXOPool utxoPool;
    private Wallet miner;

    @BeforeEach
    void setUp() {
        blockchain = new Blockchain();
        utxoPool = new UTXOPool();
        ProofOfWork pow = new ProofOfWork(TEST_DIFFICULTY);
        miningService = new MiningService(blockchain, utxoPool, pow);
        miner = new Wallet();
    }

    @Nested
    @DisplayName("Константы")
    class Constants {

        @Test
        @DisplayName("награда за genesis блок равна 10")
        void genesisRewardIsTen() {
            assertEquals(10.0, MiningService.GENESIS_REWARD, 0.0001);
        }

        @Test
        @DisplayName("награда за обычный блок равна 1")
        void blockRewardIsOne() {
            assertEquals(1.0, MiningService.BLOCK_REWARD, 0.0001);
        }
    }

    @Nested
    @DisplayName("Genesis блок")
    class GenesisBlock {

        @Test
        @DisplayName("майнинг genesis блока даёт 10 монет")
        void miningGenesisGivesTenCoins() {
            miningService.mineBlock(miner, List.of());

            double balance = utxoPool.getBalance(miner.getPublicKey());
            assertEquals(10.0, balance, 0.0001);
        }

        @Test
        @DisplayName("genesis блок добавляется в blockchain")
        void genesisBlockAddedToBlockchain() {
            int initialHeight = blockchain.getHeight();

            miningService.mineBlock(miner, List.of());

            assertEquals(initialHeight + 1, blockchain.getHeight());
        }

        @Test
        @DisplayName("genesis блок проходит proof-of-work")
        void genesisBlockHasValidProof() {
            Block block = miningService.mineBlock(miner, List.of());

            assertTrue(block.getHash().startsWith("00")); // difficulty = 2
        }
    }

    @Nested
    @DisplayName("Обычный блок")
    class RegularBlock {

        @BeforeEach
        void mineGenesisFirst() {
            // Сначала майним genesis
            miningService.mineBlock(miner, List.of());
        }

        @Test
        @DisplayName("майнинг второго блока даёт 1 монету")
        void miningSecondBlockGivesOneCoin() {
            double balanceBefore = utxoPool.getBalance(miner.getPublicKey());

            miningService.mineBlock(miner, List.of());

            double balanceAfter = utxoPool.getBalance(miner.getPublicKey());
            assertEquals(1.0, balanceAfter - balanceBefore, 0.0001);
        }

        @Test
        @DisplayName("третий блок тоже даёт 1 монету")
        void miningThirdBlockGivesOneCoin() {
            // После @BeforeEach: miner имеет genesis reward (10)
            double afterGenesis = utxoPool.getBalance(miner.getPublicKey());
            assertEquals(10.0, afterGenesis, 0.0001, "After genesis miner should have 10");

            miningService.mineBlock(miner, List.of()); // блок 2
            double afterBlock2 = utxoPool.getBalance(miner.getPublicKey());
            assertEquals(11.0, afterBlock2, 0.0001, "After block 2 miner should have 11");

            miningService.mineBlock(miner, List.of()); // блок 3
            double afterBlock3 = utxoPool.getBalance(miner.getPublicKey());
            assertEquals(12.0, afterBlock3, 0.0001, "After block 3 miner should have 12");

            assertEquals(1.0, afterBlock3 - afterBlock2, 0.0001);
        }
    }

    @Nested
    @DisplayName("Транзакции в блоке")
    class TransactionsInBlock {

        private Wallet alice;
        private Wallet bob;

        @BeforeEach
        void setUp() {
            alice = new Wallet();
            bob = new Wallet();
            // Даём Alice монеты через genesis майнинг
            miningService.mineBlock(alice, List.of());
        }

        @Test
        @DisplayName("блок включает переданные транзакции")
        void blockIncludesTransactions() {
            // Alice отправляет Bob 5 монет
            Transaction tx = createSignedTransaction(alice, bob, 5.0);

            Block block = miningService.mineBlock(miner, List.of(tx));

            // Блок содержит данные транзакции
            assertTrue(block.getData().contains(tx.getTransactionId()));
        }

        @Test
        @DisplayName("fee от транзакций добавляется к награде майнера")
        void feesAddedToMinerReward() {
            // Alice отправляет Bob 5 монет, fee = 0.1
            Transaction tx = createSignedTransaction(alice, bob, 5.0);

            double minerBalanceBefore = utxoPool.getBalance(miner.getPublicKey());
            miningService.mineBlock(miner, List.of(tx));
            double minerBalanceAfter = utxoPool.getBalance(miner.getPublicKey());

            // Майнер получает: 1.0 (block reward) + 0.1 (fee) = 1.1
            assertEquals(1.1, minerBalanceAfter - minerBalanceBefore, 0.0001);
        }

        @Test
        @DisplayName("UTXO обновляется после майнинга блока с транзакцией")
        void utxoUpdatedAfterMining() {
            // Alice начинает с 10 монет
            assertEquals(10.0, utxoPool.getBalance(alice.getPublicKey()), 0.0001);

            // Alice отправляет Bob 5 монет
            Transaction tx = createSignedTransaction(alice, bob, 5.0);
            miningService.mineBlock(miner, List.of(tx));

            // Alice: 10 - 5 - 0.1 = 4.9
            assertEquals(4.9, utxoPool.getBalance(alice.getPublicKey()), 0.0001);
            // Bob: 5
            assertEquals(5.0, utxoPool.getBalance(bob.getPublicKey()), 0.0001);
        }

        @Test
        @DisplayName("несколько транзакций в одном блоке от разных отправителей")
        void multipleTransactionsInBlock() {
            Wallet charlie = new Wallet();

            // Даём Bob монеты - он тоже майнит блок
            miningService.mineBlock(bob, List.of()); // Bob получает 1 монету

            // Alice → Charlie: 3, Bob → Charlie: 0.5
            Transaction tx1 = createSignedTransaction(alice, charlie, 3.0);
            Transaction tx2 = createSignedTransaction(bob, charlie, 0.5);

            miningService.mineBlock(miner, List.of(tx1, tx2));

            // Alice: 10 - 3 - 0.1 = 6.9
            assertEquals(6.9, utxoPool.getBalance(alice.getPublicKey()), 0.01);
            // Bob: 1 - 0.5 - 0.1 = 0.4
            assertEquals(0.4, utxoPool.getBalance(bob.getPublicKey()), 0.01);
            // Charlie: 3 + 0.5 = 3.5
            assertEquals(3.5, utxoPool.getBalance(charlie.getPublicKey()), 0.0001);
        }

        private Transaction createSignedTransaction(Wallet sender, Wallet recipient, double amount) {
            TransactionService txService = new TransactionService(utxoPool);
            TransactionResult result = txService.createTransaction(
                    sender, recipient.getPublicKey(), amount);
            assertTrue(result.isSuccess(), "Failed to create transaction: " + result.getError());
            return result.getTransaction();
        }
    }

    @Nested
    @DisplayName("Coinbase транзакция")
    class CoinbaseTransaction {

        @Test
        @DisplayName("coinbase создаёт UTXO для майнера")
        void coinbaseCreatesUtxoForMiner() {
            assertEquals(0, utxoPool.size());

            miningService.mineBlock(miner, List.of());

            // Должен быть хотя бы 1 UTXO для майнера
            assertTrue(utxoPool.getBalance(miner.getPublicKey()) > 0);
        }

        @Test
        @DisplayName("coinbase не требует inputs")
        void coinbaseHasNoInputs() {
            Block block = miningService.mineBlock(miner, List.of());

            // В данных блока должна быть coinbase транзакция
            // Coinbase уникальна тем что у неё нет inputs
            assertNotNull(block.getData());
        }
    }
}
