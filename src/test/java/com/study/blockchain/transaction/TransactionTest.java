package com.study.blockchain.transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.*;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {

    private KeyPair aliceKeys;
    private KeyPair bobKeys;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(256);
        aliceKeys = keyGen.generateKeyPair();
        bobKeys = keyGen.generateKeyPair();
    }

    @Nested
    @DisplayName("TransactionOutput")
    class TransactionOutputTests {

        @Test
        @DisplayName("создаётся с правильными значениями")
        void createsWithCorrectValues() {
            TransactionOutput output = new TransactionOutput(
                aliceKeys.getPublic(), 50.0, "parent-tx-id");

            assertEquals(aliceKeys.getPublic(), output.getRecipientPublicKey());
            assertEquals(50.0, output.getAmount());
            assertEquals("parent-tx-id", output.getParentTransactionId());
        }

        @Test
        @DisplayName("генерирует уникальный id")
        void generatesUniqueId() {
            TransactionOutput output1 = new TransactionOutput(
                aliceKeys.getPublic(), 50.0, "tx1");
            TransactionOutput output2 = new TransactionOutput(
                aliceKeys.getPublic(), 50.0, "tx2");

            assertNotNull(output1.getId());
            assertNotNull(output2.getId());
            assertNotEquals(output1.getId(), output2.getId());
        }

        @Test
        @DisplayName("id имеет формат SHA-256 (64 символа hex)")
        void idIsSha256Format() {
            TransactionOutput output = new TransactionOutput(
                aliceKeys.getPublic(), 100.0, "test-tx");

            assertEquals(64, output.getId().length());
            assertTrue(output.getId().matches("[a-f0-9]+"));
        }

        @Test
        @DisplayName("isMine возвращает true для владельца")
        void isMineReturnsTrueForOwner() {
            TransactionOutput output = new TransactionOutput(
                aliceKeys.getPublic(), 50.0, "tx");

            assertTrue(output.isMine(aliceKeys.getPublic()));
        }

        @Test
        @DisplayName("isMine возвращает false для другого ключа")
        void isMineReturnsFalseForOther() {
            TransactionOutput output = new TransactionOutput(
                aliceKeys.getPublic(), 50.0, "tx");

            assertFalse(output.isMine(bobKeys.getPublic()));
        }
    }

    @Nested
    @DisplayName("TransactionInput")
    class TransactionInputTests {

        @Test
        @DisplayName("создаётся с outputId")
        void createsWithOutputId() {
            TransactionInput input = new TransactionInput("output-123");

            assertEquals("output-123", input.getTransactionOutputId());
        }

        @Test
        @DisplayName("UTXO изначально null")
        void utxoInitiallyNull() {
            TransactionInput input = new TransactionInput("output-123");

            assertNull(input.getUTXO());
        }

        @Test
        @DisplayName("можно установить UTXO")
        void canSetUtxo() {
            TransactionInput input = new TransactionInput("output-123");
            TransactionOutput utxo = new TransactionOutput(
                aliceKeys.getPublic(), 50.0, "parent");

            input.setUTXO(utxo);

            assertEquals(utxo, input.getUTXO());
        }

        @Test
        @DisplayName("signature изначально null")
        void signatureInitiallyNull() {
            TransactionInput input = new TransactionInput("output-123");

            assertNull(input.getSignature());
        }
    }

    @Nested
    @DisplayName("Transaction")
    class TransactionTests {

        @Test
        @DisplayName("создаётся с пустыми списками")
        void createsWithEmptyLists() {
            Transaction tx = new Transaction();

            assertNotNull(tx.getInputs());
            assertNotNull(tx.getOutputs());
            assertTrue(tx.getInputs().isEmpty());
            assertTrue(tx.getOutputs().isEmpty());
        }

        @Test
        @DisplayName("можно добавлять inputs")
        void canAddInputs() {
            Transaction tx = new Transaction();
            TransactionInput input = new TransactionInput("output-1");

            tx.addInput(input);

            assertEquals(1, tx.getInputs().size());
            assertEquals(input, tx.getInputs().get(0));
        }

        @Test
        @DisplayName("можно добавлять outputs")
        void canAddOutputs() {
            Transaction tx = new Transaction();
            TransactionOutput output = new TransactionOutput(
                aliceKeys.getPublic(), 50.0, "parent");

            tx.addOutput(output);

            assertEquals(1, tx.getOutputs().size());
        }

        @Test
        @DisplayName("calculateHash возвращает валидный SHA-256")
        void calculateHashReturnsSha256() {
            Transaction tx = new Transaction();
            tx.addOutput(new TransactionOutput(aliceKeys.getPublic(), 50.0, "p1"));

            String hash = tx.calculateHash();

            assertNotNull(hash);
            assertEquals(64, hash.length());
            assertTrue(hash.matches("[a-f0-9]+"));
        }

        @Test
        @DisplayName("hash меняется при изменении outputs")
        void hashChangesWithOutputs() {
            Transaction tx1 = new Transaction();
            tx1.addOutput(new TransactionOutput(aliceKeys.getPublic(), 50.0, "p"));

            Transaction tx2 = new Transaction();
            tx2.addOutput(new TransactionOutput(aliceKeys.getPublic(), 100.0, "p"));

            assertNotEquals(tx1.calculateHash(), tx2.calculateHash());
        }

        @Test
        @DisplayName("getInputsValue считает сумму UTXO")
        void getInputsValueSumsUtxos() {
            Transaction tx = new Transaction();

            TransactionInput input1 = new TransactionInput("out1");
            input1.setUTXO(new TransactionOutput(aliceKeys.getPublic(), 30.0, "p"));

            TransactionInput input2 = new TransactionInput("out2");
            input2.setUTXO(new TransactionOutput(aliceKeys.getPublic(), 20.0, "p"));

            tx.addInput(input1);
            tx.addInput(input2);

            assertEquals(50.0, tx.getInputsValue(), 0.001);
        }

        @Test
        @DisplayName("getOutputsValue считает сумму outputs")
        void getOutputsValueSumsOutputs() {
            Transaction tx = new Transaction();
            tx.addOutput(new TransactionOutput(aliceKeys.getPublic(), 30.0, "p"));
            tx.addOutput(new TransactionOutput(bobKeys.getPublic(), 15.0, "p"));

            assertEquals(45.0, tx.getOutputsValue(), 0.001);
        }

        @Test
        @DisplayName("пустая транзакция: inputsValue = 0")
        void emptyTransactionInputsValueZero() {
            Transaction tx = new Transaction();

            assertEquals(0.0, tx.getInputsValue(), 0.001);
        }

        @Test
        @DisplayName("пустая транзакция: outputsValue = 0")
        void emptyTransactionOutputsValueZero() {
            Transaction tx = new Transaction();

            assertEquals(0.0, tx.getOutputsValue(), 0.001);
        }
    }

    @Nested
    @DisplayName("Сценарии использования")
    class UsageScenarios {

        @Test
        @DisplayName("Alice отправляет Bob 30 из 50 (сдача 20)")
        void aliceSendsBobWithChange() {
            // Alice имеет UTXO на 50
            TransactionOutput aliceUtxo = new TransactionOutput(
                aliceKeys.getPublic(), 50.0, "genesis");

            // Создаём транзакцию
            Transaction tx = new Transaction();

            // Input: тратим UTXO Alice
            TransactionInput input = new TransactionInput(aliceUtxo.getId());
            input.setUTXO(aliceUtxo);
            tx.addInput(input);

            // Outputs: 30 Bob, 20 сдача Alice
            tx.addOutput(new TransactionOutput(bobKeys.getPublic(), 30.0, "pending"));
            tx.addOutput(new TransactionOutput(aliceKeys.getPublic(), 20.0, "pending"));

            // Проверяем баланс
            assertEquals(50.0, tx.getInputsValue(), 0.001);
            assertEquals(50.0, tx.getOutputsValue(), 0.001);
        }

        @Test
        @DisplayName("Комиссия = inputs - outputs")
        void feeCalculation() {
            TransactionOutput utxo = new TransactionOutput(
                aliceKeys.getPublic(), 100.0, "prev");

            Transaction tx = new Transaction();

            TransactionInput input = new TransactionInput(utxo.getId());
            input.setUTXO(utxo);
            tx.addInput(input);

            // Отправляем только 99.5, остальное — комиссия
            tx.addOutput(new TransactionOutput(bobKeys.getPublic(), 99.5, "pending"));

            double fee = tx.getInputsValue() - tx.getOutputsValue();
            assertEquals(0.5, fee, 0.001);
        }
    }
}
