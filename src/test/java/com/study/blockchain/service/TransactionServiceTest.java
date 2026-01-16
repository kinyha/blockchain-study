package com.study.blockchain.service;

import com.study.blockchain.transaction.Transaction;
import com.study.blockchain.transaction.TransactionOutput;
import com.study.blockchain.utxo.UTXOPool;
import com.study.blockchain.wallet.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD тесты для TransactionService.
 * Бизнес-правила:
 * - Fee: 0.1 coins (фиксированная)
 * - Min amount: 0.1 coins
 * - Reject if: balance < amount + fee
 */
class TransactionServiceTest {

    private TransactionService transactionService;
    private UTXOPool utxoPool;
    private Wallet alice;
    private Wallet bob;

    @BeforeEach
    void setUp() {
        utxoPool = new UTXOPool();
        transactionService = new TransactionService(utxoPool);
        alice = new Wallet();
        bob = new Wallet();
    }

    @Nested
    @DisplayName("Константы")
    class Constants {

        @Test
        @DisplayName("комиссия равна 0.1")
        void feeIsPointOne() {
            assertEquals(0.1, TransactionService.TRANSACTION_FEE, 0.0001);
        }

        @Test
        @DisplayName("минимальная сумма перевода равна 0.1")
        void minAmountIsPointOne() {
            assertEquals(0.1, TransactionService.MIN_TRANSACTION_AMOUNT, 0.0001);
        }
    }

    @Nested
    @DisplayName("Валидация суммы")
    class AmountValidation {

        @Test
        @DisplayName("отклоняет сумму меньше минимальной")
        void rejectsAmountBelowMinimum() {
            giveAliceCoins(10.0);

            TransactionResult result = transactionService.createTransaction(
                    alice, bob.getPublicKey(), 0.05);

            assertFalse(result.isSuccess());
            assertEquals("Amount below minimum: 0.1", result.getError());
        }

        @Test
        @DisplayName("принимает сумму равную минимальной")
        void acceptsMinimumAmount() {
            giveAliceCoins(10.0);

            TransactionResult result = transactionService.createTransaction(
                    alice, bob.getPublicKey(), 0.1);

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("отклоняет отрицательную сумму")
        void rejectsNegativeAmount() {
            giveAliceCoins(10.0);

            TransactionResult result = transactionService.createTransaction(
                    alice, bob.getPublicKey(), -5.0);

            assertFalse(result.isSuccess());
            assertEquals("Amount must be positive", result.getError());
        }

        @Test
        @DisplayName("отклоняет нулевую сумму")
        void rejectsZeroAmount() {
            giveAliceCoins(10.0);

            TransactionResult result = transactionService.createTransaction(
                    alice, bob.getPublicKey(), 0.0);

            assertFalse(result.isSuccess());
            assertEquals("Amount must be positive", result.getError());
        }
    }

    @Nested
    @DisplayName("Валидация баланса")
    class BalanceValidation {

        @Test
        @DisplayName("отклоняет если баланс меньше суммы + комиссия")
        void rejectsIfBalanceLessThanAmountPlusFee() {
            giveAliceCoins(1.0);

            // Пытаемся отправить 1.0, нужно 1.0 + 0.1 = 1.1
            TransactionResult result = transactionService.createTransaction(
                    alice, bob.getPublicKey(), 1.0);

            assertFalse(result.isSuccess());
            assertEquals("Insufficient balance: have 1.0, need 1.1", result.getError());
        }

        @Test
        @DisplayName("принимает если баланс равен сумме + комиссия")
        void acceptsIfBalanceEqualsAmountPlusFee() {
            giveAliceCoins(1.1);

            TransactionResult result = transactionService.createTransaction(
                    alice, bob.getPublicKey(), 1.0);

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("отклоняет если нет UTXO")
        void rejectsIfNoUtxos() {
            // У Alice нет монет

            TransactionResult result = transactionService.createTransaction(
                    alice, bob.getPublicKey(), 1.0);

            assertFalse(result.isSuccess());
            assertEquals("Insufficient balance: have 0.0, need 1.1", result.getError());
        }
    }

    @Nested
    @DisplayName("Создание транзакции")
    class TransactionCreation {

        @Test
        @DisplayName("создаёт транзакцию с правильной суммой получателю")
        void createsTransactionWithCorrectRecipientAmount() {
            giveAliceCoins(10.0);

            TransactionResult result = transactionService.createTransaction(
                    alice, bob.getPublicKey(), 5.0);

            assertTrue(result.isSuccess());
            Transaction tx = result.getTransaction();

            // Находим output для Bob
            TransactionOutput bobOutput = findOutputFor(tx, bob.getPublicKey());
            assertNotNull(bobOutput);
            assertEquals(5.0, bobOutput.getAmount(), 0.0001);
        }

        @Test
        @DisplayName("создаёт транзакцию со сдачей отправителю")
        void createsTransactionWithChange() {
            giveAliceCoins(10.0);

            TransactionResult result = transactionService.createTransaction(
                    alice, bob.getPublicKey(), 5.0);

            assertTrue(result.isSuccess());
            Transaction tx = result.getTransaction();

            // Сдача = 10.0 - 5.0 - 0.1 = 4.9
            TransactionOutput aliceChange = findOutputFor(tx, alice.getPublicKey());
            assertNotNull(aliceChange);
            assertEquals(4.9, aliceChange.getAmount(), 0.0001);
        }

        @Test
        @DisplayName("не создаёт output для сдачи если она нулевая")
        void noChangeOutputIfZeroChange() {
            giveAliceCoins(5.1); // 5.0 + 0.1 fee = точно

            TransactionResult result = transactionService.createTransaction(
                    alice, bob.getPublicKey(), 5.0);

            assertTrue(result.isSuccess());
            Transaction tx = result.getTransaction();

            // Только один output - для Bob
            assertEquals(1, tx.getOutputs().size());
            assertTrue(tx.getOutputs().get(0).isMine(bob.getPublicKey()));
        }

        @Test
        @DisplayName("комиссия = inputs - outputs")
        void feeIsInputsMinusOutputs() {
            giveAliceCoins(10.0);

            TransactionResult result = transactionService.createTransaction(
                    alice, bob.getPublicKey(), 5.0);

            assertTrue(result.isSuccess());
            Transaction tx = result.getTransaction();

            double fee = tx.getInputsValue() - tx.getOutputsValue();
            assertEquals(0.1, fee, 0.0001);
        }

        @Test
        @DisplayName("транзакция подписана")
        void transactionIsSigned() {
            giveAliceCoins(10.0);

            TransactionResult result = transactionService.createTransaction(
                    alice, bob.getPublicKey(), 5.0);

            assertTrue(result.isSuccess());
            Transaction tx = result.getTransaction();

            assertTrue(tx.verifySignatures());
        }

        @Test
        @DisplayName("транзакция имеет transactionId после finalize")
        void transactionHasId() {
            giveAliceCoins(10.0);

            TransactionResult result = transactionService.createTransaction(
                    alice, bob.getPublicKey(), 5.0);

            assertTrue(result.isSuccess());
            Transaction tx = result.getTransaction();

            assertNotNull(tx.getTransactionId());
            assertEquals(64, tx.getTransactionId().length());
        }
    }

    @Nested
    @DisplayName("Выбор UTXO")
    class UtxoSelection {

        @Test
        @DisplayName("использует несколько UTXO если одного недостаточно")
        void usesMultipleUtxosIfNeeded() {
            // Даём Alice три UTXO по 2.0
            giveAliceCoins(2.0);
            giveAliceCoins(2.0);
            giveAliceCoins(2.0);

            // Отправляем 5.0, нужно минимум 3 UTXO (5.0 + 0.1 = 5.1)
            TransactionResult result = transactionService.createTransaction(
                    alice, bob.getPublicKey(), 5.0);

            assertTrue(result.isSuccess());
            Transaction tx = result.getTransaction();

            // Должно быть 3 input'а
            assertEquals(3, tx.getInputs().size());
        }

        @Test
        @DisplayName("резервирует UTXO при создании транзакции")
        void reservesUtxosOnCreation() {
            TransactionOutput utxo = giveAliceCoins(10.0);

            transactionService.createTransaction(alice, bob.getPublicKey(), 5.0);

            assertTrue(utxoPool.isReserved(utxo.getId()));
        }
    }

    // === Helper methods ===

    private TransactionOutput giveAliceCoins(double amount) {
        TransactionOutput utxo = new TransactionOutput(
                alice.getPublicKey(), amount, "genesis-" + System.nanoTime());
        utxoPool.addUTXO(utxo);
        return utxo;
    }

    private TransactionOutput findOutputFor(Transaction tx, PublicKey publicKey) {
        for (TransactionOutput output : tx.getOutputs()) {
            if (output.isMine(publicKey)) {
                return output;
            }
        }
        return null;
    }
}
