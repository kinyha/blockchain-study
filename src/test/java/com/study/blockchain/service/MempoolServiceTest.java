package com.study.blockchain.service;

import com.study.blockchain.transaction.Transaction;
import com.study.blockchain.transaction.TransactionOutput;
import com.study.blockchain.utxo.UTXOPool;
import com.study.blockchain.wallet.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD тесты для MempoolService.
 * Бизнес-правила:
 * - Хранит pending транзакции
 * - Удаляет транзакции после майнинга
 * - Возвращает транзакции из orphan блоков в mempool
 */
class MempoolServiceTest {

    private MempoolService mempoolService;
    private UTXOPool utxoPool;
    private Wallet alice;
    private Wallet bob;

    @BeforeEach
    void setUp() {
        utxoPool = new UTXOPool();
        mempoolService = new MempoolService(utxoPool);
        alice = new Wallet();
        bob = new Wallet();

        // Даём Alice монеты
        giveCoins(alice, 100.0);
    }

    @Nested
    @DisplayName("Добавление транзакций")
    class AddingTransactions {

        @Test
        @DisplayName("можно добавить транзакцию в mempool")
        void canAddTransaction() {
            Transaction tx = createTransaction(alice, bob, 5.0);

            boolean added = mempoolService.addTransaction(tx);

            assertTrue(added);
            assertEquals(1, mempoolService.size());
        }

        @Test
        @DisplayName("можно добавить несколько транзакций")
        void canAddMultipleTransactions() {
            Transaction tx1 = createTransaction(alice, bob, 5.0);
            releaseReservations(tx1);
            Transaction tx2 = createTransaction(alice, bob, 3.0);

            mempoolService.addTransaction(tx1);
            mempoolService.addTransaction(tx2);

            assertEquals(2, mempoolService.size());
        }

        @Test
        @DisplayName("нельзя добавить одну транзакцию дважды")
        void cannotAddSameTransactionTwice() {
            Transaction tx = createTransaction(alice, bob, 5.0);

            assertTrue(mempoolService.addTransaction(tx));
            assertFalse(mempoolService.addTransaction(tx));
            assertEquals(1, mempoolService.size());
        }

        @Test
        @DisplayName("транзакция должна быть подписана")
        void transactionMustBeSigned() {
            Transaction tx = new Transaction();
            tx.addOutput(new TransactionOutput(bob.getPublicKey(), 5.0, "test"));
            tx.finalizeTransaction();
            // Не подписываем

            boolean added = mempoolService.addTransaction(tx);

            assertFalse(added);
        }
    }

    @Nested
    @DisplayName("Получение транзакций")
    class GettingTransactions {

        @Test
        @DisplayName("getPendingTransactions возвращает все транзакции")
        void getPendingTransactionsReturnsAll() {
            Transaction tx1 = createTransaction(alice, bob, 5.0);
            releaseReservations(tx1);
            Transaction tx2 = createTransaction(alice, bob, 3.0);

            mempoolService.addTransaction(tx1);
            mempoolService.addTransaction(tx2);

            List<Transaction> pending = mempoolService.getPendingTransactions();
            assertEquals(2, pending.size());
        }

        @Test
        @DisplayName("getPendingTransactions возвращает копию списка")
        void getPendingTransactionsReturnsCopy() {
            Transaction tx = createTransaction(alice, bob, 5.0);
            mempoolService.addTransaction(tx);

            List<Transaction> pending = mempoolService.getPendingTransactions();
            pending.clear();

            assertEquals(1, mempoolService.size());
        }

        @Test
        @DisplayName("getTransactionsForBlock возвращает указанное количество")
        void getTransactionsForBlockReturnsLimit() {
            for (int i = 0; i < 5; i++) {
                Transaction tx = createTransaction(alice, bob, 1.0);
                mempoolService.addTransaction(tx);
                releaseReservations(tx);
            }

            List<Transaction> forBlock = mempoolService.getTransactionsForBlock(3);
            assertEquals(3, forBlock.size());
        }

        @Test
        @DisplayName("getTransactionsForBlock возвращает все если меньше лимита")
        void getTransactionsForBlockReturnsAllIfLessThanLimit() {
            Transaction tx1 = createTransaction(alice, bob, 5.0);
            releaseReservations(tx1);
            Transaction tx2 = createTransaction(alice, bob, 3.0);

            mempoolService.addTransaction(tx1);
            mempoolService.addTransaction(tx2);

            List<Transaction> forBlock = mempoolService.getTransactionsForBlock(10);
            assertEquals(2, forBlock.size());
        }

        @Test
        @DisplayName("contains проверяет наличие транзакции")
        void containsChecksPresence() {
            Transaction tx = createTransaction(alice, bob, 5.0);
            mempoolService.addTransaction(tx);

            assertTrue(mempoolService.contains(tx.getTransactionId()));
            assertFalse(mempoolService.contains("nonexistent"));
        }
    }

    @Nested
    @DisplayName("Удаление транзакций")
    class RemovingTransactions {

        @Test
        @DisplayName("removeTransaction удаляет транзакцию")
        void removeTransactionRemoves() {
            Transaction tx = createTransaction(alice, bob, 5.0);
            mempoolService.addTransaction(tx);

            mempoolService.removeTransaction(tx.getTransactionId());

            assertEquals(0, mempoolService.size());
            assertFalse(mempoolService.contains(tx.getTransactionId()));
        }

        @Test
        @DisplayName("removeTransactions удаляет список транзакций")
        void removeTransactionsRemovesList() {
            Transaction tx1 = createTransaction(alice, bob, 5.0);
            releaseReservations(tx1);
            Transaction tx2 = createTransaction(alice, bob, 3.0);
            releaseReservations(tx2);
            Transaction tx3 = createTransaction(alice, bob, 2.0);

            mempoolService.addTransaction(tx1);
            mempoolService.addTransaction(tx2);
            mempoolService.addTransaction(tx3);

            mempoolService.removeTransactions(List.of(tx1, tx2));

            assertEquals(1, mempoolService.size());
            assertTrue(mempoolService.contains(tx3.getTransactionId()));
        }

        @Test
        @DisplayName("clear очищает весь mempool")
        void clearRemovesAll() {
            mempoolService.addTransaction(createTransaction(alice, bob, 5.0));
            releaseReservations();
            mempoolService.addTransaction(createTransaction(alice, bob, 3.0));

            mempoolService.clear();

            assertEquals(0, mempoolService.size());
        }
    }

    @Nested
    @DisplayName("Orphan блоки")
    class OrphanBlocks {

        @Test
        @DisplayName("returnToMempool возвращает транзакции в mempool")
        void returnToMempoolReturnsTransactions() {
            Transaction tx1 = createTransaction(alice, bob, 5.0);
            releaseReservations(tx1);
            Transaction tx2 = createTransaction(alice, bob, 3.0);

            // Транзакции были в блоке (не в mempool)
            assertEquals(0, mempoolService.size());

            // Блок стал orphan, возвращаем транзакции
            mempoolService.returnToMempool(List.of(tx1, tx2));

            assertEquals(2, mempoolService.size());
            assertTrue(mempoolService.contains(tx1.getTransactionId()));
            assertTrue(mempoolService.contains(tx2.getTransactionId()));
        }

        @Test
        @DisplayName("returnToMempool не дублирует существующие транзакции")
        void returnToMempoolDoesNotDuplicate() {
            Transaction tx = createTransaction(alice, bob, 5.0);
            mempoolService.addTransaction(tx);

            mempoolService.returnToMempool(List.of(tx));

            assertEquals(1, mempoolService.size());
        }
    }

    // === Helper methods ===

    private void giveCoins(Wallet wallet, double amount) {
        TransactionOutput utxo = new TransactionOutput(
                wallet.getPublicKey(), amount, "genesis-" + System.nanoTime());
        utxoPool.addUTXO(utxo);
    }

    private Transaction createTransaction(Wallet sender, Wallet recipient, double amount) {
        TransactionService txService = new TransactionService(utxoPool);
        TransactionResult result = txService.createTransaction(
                sender, recipient.getPublicKey(), amount);
        assertTrue(result.isSuccess(), "Failed to create transaction: " + result.getError());
        return result.getTransaction();
    }

    private void releaseReservations(Transaction tx) {
        for (var input : tx.getInputs()) {
            utxoPool.releaseReservation(input.getTransactionOutputId());
        }
    }

    private void releaseReservations() {
        // Release all reservations by getting all UTXOs and releasing them
        for (var utxo : utxoPool.getAllUTXOs()) {
            utxoPool.releaseReservation(utxo.getId());
        }
    }
}
