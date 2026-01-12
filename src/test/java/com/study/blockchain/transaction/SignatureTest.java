package com.study.blockchain.transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.*;

import static org.junit.jupiter.api.Assertions.*;

class SignatureTest {

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
    @DisplayName("TransactionInput подпись")
    class InputSignature {

        @Test
        @DisplayName("подписывает input приватным ключом")
        void signsInput() {
            TransactionInput input = new TransactionInput("output-123");
            input.setUTXO(new TransactionOutput(aliceKeys.getPublic(), 50.0, "parent"));

            input.sign(aliceKeys.getPrivate());

            assertNotNull(input.getSignature());
            assertTrue(input.getSignature().length > 0);
        }

        @Test
        @DisplayName("подпись верифицируется публичным ключом владельца")
        void verifiesWithOwnerKey() {
            TransactionInput input = new TransactionInput("output-123");
            input.setUTXO(new TransactionOutput(aliceKeys.getPublic(), 50.0, "parent"));

            input.sign(aliceKeys.getPrivate());

            assertTrue(input.verifySignature(aliceKeys.getPublic()));
        }

        @Test
        @DisplayName("подпись не проходит с чужим публичным ключом")
        void failsWithWrongKey() {
            TransactionInput input = new TransactionInput("output-123");
            input.setUTXO(new TransactionOutput(aliceKeys.getPublic(), 50.0, "parent"));

            input.sign(aliceKeys.getPrivate());

            assertFalse(input.verifySignature(bobKeys.getPublic()));
        }

        @Test
        @DisplayName("неподписанный input не проходит верификацию")
        void unsignedInputFails() {
            TransactionInput input = new TransactionInput("output-123");
            input.setUTXO(new TransactionOutput(aliceKeys.getPublic(), 50.0, "parent"));

            assertFalse(input.verifySignature(aliceKeys.getPublic()));
        }
    }

    @Nested
    @DisplayName("Transaction подпись")
    class TxSignature {

        @Test
        @DisplayName("подписывает все inputs транзакции")
        void signsAllInputs() {
            Transaction tx = new Transaction();

            TransactionInput input1 = new TransactionInput("out1");
            input1.setUTXO(new TransactionOutput(aliceKeys.getPublic(), 30.0, "p"));

            TransactionInput input2 = new TransactionInput("out2");
            input2.setUTXO(new TransactionOutput(aliceKeys.getPublic(), 20.0, "p"));

            tx.addInput(input1);
            tx.addInput(input2);

            tx.signAllInputs(aliceKeys.getPrivate());

            assertNotNull(input1.getSignature());
            assertNotNull(input2.getSignature());
        }

        @Test
        @DisplayName("verifySignatures проходит для валидной транзакции")
        void verifiesValidTransaction() {
            Transaction tx = new Transaction();

            TransactionInput input = new TransactionInput("out1");
            input.setUTXO(new TransactionOutput(aliceKeys.getPublic(), 50.0, "p"));
            tx.addInput(input);

            tx.addOutput(new TransactionOutput(bobKeys.getPublic(), 50.0, "pending"));

            tx.signAllInputs(aliceKeys.getPrivate());

            assertTrue(tx.verifySignatures());
        }

        @Test
        @DisplayName("verifySignatures провал если input не подписан")
        void failsIfInputNotSigned() {
            Transaction tx = new Transaction();

            TransactionInput input = new TransactionInput("out1");
            input.setUTXO(new TransactionOutput(aliceKeys.getPublic(), 50.0, "p"));
            tx.addInput(input);

            assertFalse(tx.verifySignatures());
        }

        @Test
        @DisplayName("verifySignatures провал если подписано неправильным ключом")
        void failsIfWrongKeySigned() {
            Transaction tx = new Transaction();

            TransactionInput input = new TransactionInput("out1");
            input.setUTXO(new TransactionOutput(aliceKeys.getPublic(), 50.0, "p"));
            tx.addInput(input);

            // Подписываем ключом Bob, но UTXO принадлежит Alice
            tx.signAllInputs(bobKeys.getPrivate());

            assertFalse(tx.verifySignatures());
        }

        @Test
        @DisplayName("verifySignatures провал если UTXO не установлен")
        void failsIfUtxoMissing() {
            Transaction tx = new Transaction();

            TransactionInput input = new TransactionInput("out1");
            // UTXO не установлен
            tx.addInput(input);

            assertFalse(tx.verifySignatures());
        }

        @Test
        @DisplayName("пустая транзакция проходит верификацию")
        void emptyTransactionPasses() {
            Transaction tx = new Transaction();

            assertTrue(tx.verifySignatures());
        }
    }

    @Nested
    @DisplayName("Сценарии использования")
    class UsageScenarios {

        @Test
        @DisplayName("Alice отправляет Bob с валидной подписью")
        void aliceSendsToBobSigned() {
            // Alice имеет UTXO
            TransactionOutput aliceUtxo = new TransactionOutput(
                aliceKeys.getPublic(), 100.0, "genesis");

            // Создаём транзакцию
            Transaction tx = new Transaction();

            TransactionInput input = new TransactionInput(aliceUtxo.getId());
            input.setUTXO(aliceUtxo);
            tx.addInput(input);

            tx.addOutput(new TransactionOutput(bobKeys.getPublic(), 70.0, "pending"));
            tx.addOutput(new TransactionOutput(aliceKeys.getPublic(), 30.0, "pending"));

            // Alice подписывает
            tx.signAllInputs(aliceKeys.getPrivate());

            // Верификация
            assertTrue(tx.verifySignatures());
            assertEquals(100.0, tx.getInputsValue(), 0.001);
            assertEquals(100.0, tx.getOutputsValue(), 0.001);
        }

        @Test
        @DisplayName("злоумышленник не может потратить чужой UTXO")
        void attackerCannotSpendOthersUtxo() {
            // UTXO принадлежит Alice
            TransactionOutput aliceUtxo = new TransactionOutput(
                aliceKeys.getPublic(), 100.0, "genesis");

            // Bob пытается потратить
            Transaction tx = new Transaction();
            TransactionInput input = new TransactionInput(aliceUtxo.getId());
            input.setUTXO(aliceUtxo);
            tx.addInput(input);

            tx.addOutput(new TransactionOutput(bobKeys.getPublic(), 100.0, "pending"));

            // Bob подписывает своим ключом
            tx.signAllInputs(bobKeys.getPrivate());

            // Верификация должна провалиться
            assertFalse(tx.verifySignatures());
        }
    }
}
