package com.study.blockchain.utxo;

import com.study.blockchain.transaction.TransactionOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UTXOPoolTest {

    private UTXOPool pool;
    private KeyPair aliceKeys;
    private KeyPair bobKeys;

    @BeforeEach
    void setUp() throws Exception {
        pool = new UTXOPool();
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(256);
        aliceKeys = keyGen.generateKeyPair();
        bobKeys = keyGen.generateKeyPair();
    }

    @Nested
    @DisplayName("Базовые операции")
    class BasicOperations {

        @Test
        @DisplayName("новый pool пустой")
        void newPoolIsEmpty() {
            assertEquals(0, pool.size());
        }

        @Test
        @DisplayName("addUTXO увеличивает размер")
        void addUtxoIncreasesSize() {
            TransactionOutput output = new TransactionOutput(
                aliceKeys.getPublic(), 50.0, "tx1");

            pool.addUTXO(output);

            assertEquals(1, pool.size());
        }

        @Test
        @DisplayName("getUTXO возвращает добавленный output")
        void getUtxoReturnsAddedOutput() {
            TransactionOutput output = new TransactionOutput(
                aliceKeys.getPublic(), 50.0, "tx1");
            pool.addUTXO(output);

            TransactionOutput retrieved = pool.getUTXO(output.getId());

            assertEquals(output, retrieved);
        }

        @Test
        @DisplayName("contains возвращает true для существующего UTXO")
        void containsReturnsTrueForExisting() {
            TransactionOutput output = new TransactionOutput(
                aliceKeys.getPublic(), 50.0, "tx1");
            pool.addUTXO(output);

            assertTrue(pool.contains(output.getId()));
        }

        @Test
        @DisplayName("contains возвращает false для несуществующего UTXO")
        void containsReturnsFalseForMissing() {
            assertFalse(pool.contains("nonexistent-id"));
        }

        @Test
        @DisplayName("removeUTXO удаляет output")
        void removeUtxoRemovesOutput() {
            TransactionOutput output = new TransactionOutput(
                aliceKeys.getPublic(), 50.0, "tx1");
            pool.addUTXO(output);

            pool.removeUTXO(output.getId());

            assertFalse(pool.contains(output.getId()));
            assertEquals(0, pool.size());
        }

        @Test
        @DisplayName("getAllUTXOs возвращает все outputs")
        void getAllUtxosReturnsAll() {
            pool.addUTXO(new TransactionOutput(aliceKeys.getPublic(), 50.0, "tx1"));
            pool.addUTXO(new TransactionOutput(bobKeys.getPublic(), 30.0, "tx2"));

            assertEquals(2, pool.getAllUTXOs().size());
        }
    }

    @Nested
    @DisplayName("Баланс")
    class BalanceTests {

        @Test
        @DisplayName("баланс пустого pool = 0")
        void emptyPoolBalanceIsZero() {
            assertEquals(0.0, pool.getBalance(aliceKeys.getPublic()), 0.001);
        }

        @Test
        @DisplayName("баланс считается правильно")
        void balanceCalculatedCorrectly() {
            pool.addUTXO(new TransactionOutput(aliceKeys.getPublic(), 50.0, "tx1"));
            pool.addUTXO(new TransactionOutput(aliceKeys.getPublic(), 30.0, "tx2"));
            pool.addUTXO(new TransactionOutput(bobKeys.getPublic(), 100.0, "tx3"));

            assertEquals(80.0, pool.getBalance(aliceKeys.getPublic()), 0.001);
            assertEquals(100.0, pool.getBalance(bobKeys.getPublic()), 0.001);
        }

        @Test
        @DisplayName("баланс обновляется при удалении UTXO")
        void balanceUpdatesOnRemove() {
            TransactionOutput output1 = new TransactionOutput(
                aliceKeys.getPublic(), 50.0, "tx1");
            TransactionOutput output2 = new TransactionOutput(
                aliceKeys.getPublic(), 30.0, "tx2");

            pool.addUTXO(output1);
            pool.addUTXO(output2);
            assertEquals(80.0, pool.getBalance(aliceKeys.getPublic()), 0.001);

            pool.removeUTXO(output1.getId());
            assertEquals(30.0, pool.getBalance(aliceKeys.getPublic()), 0.001);
        }
    }

    @Nested
    @DisplayName("getUTXOsForAddress")
    class GetUtxosForAddressTests {

        @Test
        @DisplayName("возвращает пустой список для неизвестного адреса")
        void returnsEmptyListForUnknownAddress() {
            List<TransactionOutput> utxos = pool.getUTXOsForAddress(aliceKeys.getPublic());

            assertTrue(utxos.isEmpty());
        }

        @Test
        @DisplayName("возвращает только UTXO владельца")
        void returnsOnlyOwnersUtxos() {
            pool.addUTXO(new TransactionOutput(aliceKeys.getPublic(), 50.0, "tx1"));
            pool.addUTXO(new TransactionOutput(aliceKeys.getPublic(), 30.0, "tx2"));
            pool.addUTXO(new TransactionOutput(bobKeys.getPublic(), 100.0, "tx3"));

            List<TransactionOutput> aliceUtxos = pool.getUTXOsForAddress(aliceKeys.getPublic());

            assertEquals(2, aliceUtxos.size());
            assertTrue(aliceUtxos.stream().allMatch(u -> u.isMine(aliceKeys.getPublic())));
        }
    }

    @Nested
    @DisplayName("Сценарии использования")
    class UsageScenarios {

        @Test
        @DisplayName("симуляция транзакции: Alice → Bob")
        void simulateTransaction() {
            // Genesis: Alice получает 100
            TransactionOutput aliceUtxo = new TransactionOutput(
                aliceKeys.getPublic(), 100.0, "genesis");
            pool.addUTXO(aliceUtxo);

            assertEquals(100.0, pool.getBalance(aliceKeys.getPublic()), 0.001);
            assertEquals(0.0, pool.getBalance(bobKeys.getPublic()), 0.001);

            // Alice отправляет Bob 70, сдача 30
            pool.removeUTXO(aliceUtxo.getId());
            pool.addUTXO(new TransactionOutput(bobKeys.getPublic(), 70.0, "tx1"));
            pool.addUTXO(new TransactionOutput(aliceKeys.getPublic(), 30.0, "tx1"));

            assertEquals(30.0, pool.getBalance(aliceKeys.getPublic()), 0.001);
            assertEquals(70.0, pool.getBalance(bobKeys.getPublic()), 0.001);
        }

        @Test
        @DisplayName("double-spend предотвращается удалением UTXO")
        void doubleSpendPrevented() {
            TransactionOutput utxo = new TransactionOutput(
                aliceKeys.getPublic(), 100.0, "tx1");
            pool.addUTXO(utxo);

            // Первая трата
            assertTrue(pool.contains(utxo.getId()));
            pool.removeUTXO(utxo.getId());

            // Вторая трата — UTXO уже нет
            assertFalse(pool.contains(utxo.getId()));
        }

        @Test
        @DisplayName("coin selection: выбор UTXO для суммы")
        void coinSelection() {
            pool.addUTXO(new TransactionOutput(aliceKeys.getPublic(), 10.0, "tx1"));
            pool.addUTXO(new TransactionOutput(aliceKeys.getPublic(), 20.0, "tx2"));
            pool.addUTXO(new TransactionOutput(aliceKeys.getPublic(), 50.0, "tx3"));

            List<TransactionOutput> utxos = pool.getUTXOsForAddress(aliceKeys.getPublic());

            // Нужно отправить 25: выбираем 10 + 20 = 30, сдача 5
            double needed = 25.0;
            double collected = 0;
            int count = 0;
            for (TransactionOutput utxo : utxos) {
                if (collected >= needed) break;
                collected += utxo.getAmount();
                count++;
            }

            assertTrue(collected >= needed);
        }
    }
}
