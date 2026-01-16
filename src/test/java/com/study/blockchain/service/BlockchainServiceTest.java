package com.study.blockchain.service;

import com.study.blockchain.core.Block;
import com.study.blockchain.core.Blockchain;
import com.study.blockchain.mining.ProofOfWork;
import com.study.blockchain.transaction.Transaction;
import com.study.blockchain.utxo.UTXOPool;
import com.study.blockchain.wallet.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD тесты для BlockchainService.
 * Интеграционный сервис, объединяющий все компоненты blockchain.
 */
class BlockchainServiceTest {

    private static final int TEST_DIFFICULTY = 2;

    private BlockchainService blockchainService;
    private Wallet miner;
    private Wallet alice;
    private Wallet bob;

    @BeforeEach
    void setUp() {
        blockchainService = new BlockchainService(TEST_DIFFICULTY);
        miner = new Wallet();
        alice = new Wallet();
        bob = new Wallet();
    }

    @Nested
    @DisplayName("Инициализация")
    class Initialization {

        @Test
        @DisplayName("создаёт blockchain с genesis блоком")
        void createsBlockchainWithGenesis() {
            assertEquals(1, blockchainService.getBlockchainSize());
            assertEquals(0, blockchainService.getChainHeight());
        }

        @Test
        @DisplayName("genesis блок не содержит транзакций")
        void genesisBlockHasNoTransactions() {
            Block genesis = blockchainService.getBlock(0);

            assertNotNull(genesis);
            assertEquals(0, genesis.getIndex());
        }
    }

    @Nested
    @DisplayName("Майнинг")
    class Mining {

        @Test
        @DisplayName("майнинг genesis блока даёт 10 coins")
        void miningGenesisBlockGives10Coins() {
            blockchainService.mineBlock(miner);

            double balance = blockchainService.getBalance(miner);
            assertEquals(MiningService.GENESIS_REWARD, balance);
        }

        @Test
        @DisplayName("майнинг обычного блока даёт 1 coin + fee")
        void miningRegularBlockGives1CoinPlusFee() {
            // Genesis mining
            blockchainService.mineBlock(miner);
            assertEquals(MiningService.GENESIS_REWARD, blockchainService.getBalance(miner));

            // Mine another block
            blockchainService.mineBlock(miner);
            double expectedBalance = MiningService.GENESIS_REWARD + MiningService.BLOCK_REWARD;
            assertEquals(expectedBalance, blockchainService.getBalance(miner));
        }

        @Test
        @DisplayName("майнинг включает pending транзакции")
        void miningIncludesPendingTransactions() {
            // Setup - give alice some coins
            blockchainService.mineBlock(alice);
            double aliceInitial = blockchainService.getBalance(alice);

            // Create transaction: Alice sends 5.0 to Bob
            TransactionResult txResult = blockchainService.createTransaction(
                    alice, bob.getPublicKey(), 5.0);
            assertTrue(txResult.isSuccess());

            // Mine block with bob as miner
            Block block = blockchainService.mineBlock(bob);

            // Bob should have: received amount (5.0) + block reward (1.0) + fee (0.1)
            double bobBalance = blockchainService.getBalance(bob);
            assertEquals(5.0 + MiningService.BLOCK_REWARD + TransactionService.TRANSACTION_FEE, bobBalance, 0.001);

            // Alice should have initial - amount - fee
            double aliceBalance = blockchainService.getBalance(alice);
            assertEquals(aliceInitial - 5.0 - TransactionService.TRANSACTION_FEE, aliceBalance, 0.001);
        }
    }

    @Nested
    @DisplayName("Транзакции")
    class Transactions {

        @Test
        @DisplayName("создание транзакции добавляет в mempool")
        void createTransactionAddsToMempool() {
            // Give alice coins
            blockchainService.mineBlock(alice);

            // Create transaction
            TransactionResult result = blockchainService.createTransaction(
                    alice, bob.getPublicKey(), 1.0);

            assertTrue(result.isSuccess());
            assertEquals(1, blockchainService.getPendingTransactionsCount());
        }

        @Test
        @DisplayName("транзакция с недостаточным балансом отклоняется")
        void transactionWithInsufficientBalanceRejected() {
            TransactionResult result = blockchainService.createTransaction(
                    alice, bob.getPublicKey(), 100.0);

            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("Insufficient balance"));
        }

        @Test
        @DisplayName("транзакция ниже минимума отклоняется")
        void transactionBelowMinimumRejected() {
            blockchainService.mineBlock(alice);

            TransactionResult result = blockchainService.createTransaction(
                    alice, bob.getPublicKey(), 0.01);

            assertFalse(result.isSuccess());
            assertTrue(result.getError().toLowerCase().contains("minimum"));
        }
    }

    @Nested
    @DisplayName("Запрос данных")
    class QueryingData {

        @Test
        @DisplayName("getLatestBlock возвращает последний блок")
        void getLatestBlockReturnsLastBlock() {
            Block genesis = blockchainService.getLatestBlock();
            assertEquals(0, genesis.getIndex());

            blockchainService.mineBlock(miner);
            Block second = blockchainService.getLatestBlock();
            assertEquals(1, second.getIndex());
        }

        @Test
        @DisplayName("getBlock по индексу возвращает блок")
        void getBlockByIndexReturnsBlock() {
            blockchainService.mineBlock(miner);
            blockchainService.mineBlock(miner);

            Block block = blockchainService.getBlock(1);
            assertNotNull(block);
            assertEquals(1, block.getIndex());
        }

        @Test
        @DisplayName("getBlock с неверным индексом возвращает null")
        void getBlockWithInvalidIndexReturnsNull() {
            Block block = blockchainService.getBlock(999);
            assertNull(block);
        }

        @Test
        @DisplayName("getBlocks возвращает все блоки")
        void getBlocksReturnsAllBlocks() {
            blockchainService.mineBlock(miner);
            blockchainService.mineBlock(miner);

            List<Block> blocks = blockchainService.getBlocks();
            assertEquals(3, blocks.size());
        }

        @Test
        @DisplayName("getUtxos возвращает UTXOs кошелька")
        void getUtxosReturnsWalletUtxos() {
            blockchainService.mineBlock(alice);

            var utxos = blockchainService.getUtxos(alice);
            assertFalse(utxos.isEmpty());
        }
    }

    @Nested
    @DisplayName("Валидация цепочки")
    class ChainValidation {

        @Test
        @DisplayName("isChainValid возвращает true для валидной цепочки")
        void isChainValidReturnsTrueForValidChain() {
            blockchainService.mineBlock(miner);
            blockchainService.mineBlock(miner);

            assertTrue(blockchainService.isChainValid());
        }
    }

    @Nested
    @DisplayName("Интеграция полного flow")
    class FullIntegration {

        @Test
        @DisplayName("полный цикл: mining -> transaction -> mining")
        void fullCycleMiningTransactionMining() {
            // Step 1: Miner mines genesis
            blockchainService.mineBlock(miner);
            assertEquals(MiningService.GENESIS_REWARD, blockchainService.getBalance(miner));

            // Step 2: Miner sends to Alice
            TransactionResult tx1 = blockchainService.createTransaction(
                    miner, alice.getPublicKey(), 3.0);
            assertTrue(tx1.isSuccess());

            // Step 3: Bob mines (includes tx1)
            blockchainService.mineBlock(bob);

            // Verify balances:
            // Miner: 10 - 3 - 0.1(fee) = 6.9
            assertEquals(6.9, blockchainService.getBalance(miner), 0.001);
            // Alice: 3
            assertEquals(3.0, blockchainService.getBalance(alice), 0.001);
            // Bob: 1(reward) + 0.1(fee) = 1.1
            assertEquals(1.1, blockchainService.getBalance(bob), 0.001);

            // Step 4: Alice sends to Bob
            TransactionResult tx2 = blockchainService.createTransaction(
                    alice, bob.getPublicKey(), 1.0);
            assertTrue(tx2.isSuccess());

            // Step 5: Miner mines (includes tx2)
            blockchainService.mineBlock(miner);

            // Verify final balances:
            // Miner: 6.9 + 1 + 0.1 = 8.0
            assertEquals(8.0, blockchainService.getBalance(miner), 0.001);
            // Alice: 3 - 1 - 0.1 = 1.9
            assertEquals(1.9, blockchainService.getBalance(alice), 0.001);
            // Bob: 1.1 + 1 = 2.1
            assertEquals(2.1, blockchainService.getBalance(bob), 0.001);
        }

        @Test
        @DisplayName("множество транзакций в одном блоке")
        void multipleTransactionsInOneBlock() {
            // Setup - give Alice and Bob coins
            blockchainService.mineBlock(alice);  // Alice gets 10 (genesis)
            blockchainService.mineBlock(bob);    // Bob gets 1

            // Both send to miner (Alice sends more since she has more)
            TransactionResult tx1 = blockchainService.createTransaction(alice, miner.getPublicKey(), 2.0);
            assertTrue(tx1.isSuccess(), "Alice should be able to send 2.0");

            // Bob only has 1.0, so he can only send 0.5 (after 0.1 fee)
            TransactionResult tx2 = blockchainService.createTransaction(bob, miner.getPublicKey(), 0.5);
            assertTrue(tx2.isSuccess(), "Bob should be able to send 0.5");

            assertEquals(2, blockchainService.getPendingTransactionsCount());

            // Mine with third party to keep math simple
            Wallet thirdParty = new Wallet();
            blockchainService.mineBlock(thirdParty);

            // Miner received 2 + 0.5 = 2.5
            assertEquals(2.5, blockchainService.getBalance(miner), 0.001);
            // Third party: 1 + 0.1 + 0.1 = 1.2 (reward + both fees)
            assertEquals(1.2, blockchainService.getBalance(thirdParty), 0.001);
        }
    }
}
