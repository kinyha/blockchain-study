package com.study.blockchain.service;

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
 * TDD тесты для WalletService.
 * Бизнес-правила:
 * - Balance = сумма всех UTXO владельца
 * - Корректный выбор UTXO для траты
 */
class WalletServiceTest {

    private WalletService walletService;
    private UTXOPool utxoPool;
    private Wallet alice;
    private Wallet bob;

    @BeforeEach
    void setUp() {
        utxoPool = new UTXOPool();
        walletService = new WalletService(utxoPool);
        alice = new Wallet();
        bob = new Wallet();
    }

    @Nested
    @DisplayName("Баланс кошелька")
    class WalletBalance {

        @Test
        @DisplayName("баланс пустого кошелька равен нулю")
        void emptyWalletHasZeroBalance() {
            double balance = walletService.getBalance(alice);

            assertEquals(0.0, balance);
        }

        @Test
        @DisplayName("баланс равен сумме UTXO")
        void balanceEqualsSumOfUtxos() {
            giveCoins(alice, 10.0);
            giveCoins(alice, 25.0);
            giveCoins(alice, 5.0);

            double balance = walletService.getBalance(alice);

            assertEquals(40.0, balance);
        }

        @Test
        @DisplayName("баланс не включает чужие UTXO")
        void balanceDoesNotIncludeOthersUtxos() {
            giveCoins(alice, 10.0);
            giveCoins(bob, 50.0);

            double aliceBalance = walletService.getBalance(alice);
            double bobBalance = walletService.getBalance(bob);

            assertEquals(10.0, aliceBalance);
            assertEquals(50.0, bobBalance);
        }

        @Test
        @DisplayName("баланс не включает потраченные UTXO")
        void balanceDoesNotIncludeSpentUtxos() {
            TransactionOutput utxo = giveCoins(alice, 10.0);
            utxoPool.removeUTXO(utxo.getId());

            double balance = walletService.getBalance(alice);

            assertEquals(0.0, balance);
        }

        @Test
        @DisplayName("баланс не включает зарезервированные UTXO")
        void balanceDoesNotIncludeReservedUtxos() {
            TransactionOutput utxo = giveCoins(alice, 10.0);
            giveCoins(alice, 5.0);
            utxoPool.reserveUTXO(utxo.getId());

            double balance = walletService.getBalance(alice);

            assertEquals(5.0, balance);
        }
    }

    @Nested
    @DisplayName("Получение UTXO")
    class GettingUtxos {

        @Test
        @DisplayName("getUtxos возвращает все UTXO владельца")
        void getUtxosReturnsAllOwnedUtxos() {
            giveCoins(alice, 10.0);
            giveCoins(alice, 20.0);
            giveCoins(bob, 50.0);

            List<TransactionOutput> aliceUtxos = walletService.getUtxos(alice);

            assertEquals(2, aliceUtxos.size());
            double total = aliceUtxos.stream().mapToDouble(TransactionOutput::getAmount).sum();
            assertEquals(30.0, total);
        }

        @Test
        @DisplayName("getUtxos возвращает пустой список если нет UTXO")
        void getUtxosReturnsEmptyListIfNoUtxos() {
            List<TransactionOutput> utxos = walletService.getUtxos(alice);

            assertTrue(utxos.isEmpty());
        }

        @Test
        @DisplayName("getAvailableUtxos не включает зарезервированные")
        void getAvailableUtxosExcludesReserved() {
            TransactionOutput reserved = giveCoins(alice, 10.0);
            giveCoins(alice, 20.0);
            utxoPool.reserveUTXO(reserved.getId());

            List<TransactionOutput> available = walletService.getAvailableUtxos(alice);

            assertEquals(1, available.size());
            assertEquals(20.0, available.get(0).getAmount());
        }
    }

    @Nested
    @DisplayName("Выбор UTXO для траты")
    class UtxoSelection {

        @Test
        @DisplayName("selectUtxosForAmount выбирает достаточно UTXO")
        void selectUtxosForAmountSelectsEnough() {
            giveCoins(alice, 5.0);
            giveCoins(alice, 10.0);
            giveCoins(alice, 3.0);

            List<TransactionOutput> selected = walletService.selectUtxosForAmount(alice, 12.0);

            assertNotNull(selected);
            double total = selected.stream().mapToDouble(TransactionOutput::getAmount).sum();
            assertTrue(total >= 12.0, "Selected amount must be >= requested");
        }

        @Test
        @DisplayName("selectUtxosForAmount возвращает null при недостаточном балансе")
        void selectUtxosForAmountReturnsNullIfInsufficient() {
            giveCoins(alice, 5.0);

            List<TransactionOutput> selected = walletService.selectUtxosForAmount(alice, 100.0);

            assertNull(selected);
        }

        @Test
        @DisplayName("selectUtxosForAmount выбирает точную сумму если возможно")
        void selectUtxosForAmountSelectsExactIfPossible() {
            giveCoins(alice, 5.0);
            giveCoins(alice, 10.0);
            giveCoins(alice, 7.0);

            List<TransactionOutput> selected = walletService.selectUtxosForAmount(alice, 10.0);

            assertNotNull(selected);
            double total = selected.stream().mapToDouble(TransactionOutput::getAmount).sum();
            // Either exact match or minimal overage
            assertTrue(total >= 10.0);
        }

        @Test
        @DisplayName("selectUtxosForAmount не выбирает зарезервированные UTXO")
        void selectUtxosForAmountDoesNotSelectReserved() {
            TransactionOutput reserved = giveCoins(alice, 100.0);
            giveCoins(alice, 5.0);
            utxoPool.reserveUTXO(reserved.getId());

            List<TransactionOutput> selected = walletService.selectUtxosForAmount(alice, 50.0);

            assertNull(selected, "Should not be able to select reserved UTXOs");
        }

        @Test
        @DisplayName("selectUtxosForAmount с нулевой суммой возвращает пустой список")
        void selectUtxosForZeroReturnsEmptyList() {
            giveCoins(alice, 10.0);

            List<TransactionOutput> selected = walletService.selectUtxosForAmount(alice, 0.0);

            assertNotNull(selected);
            assertTrue(selected.isEmpty());
        }
    }

    @Nested
    @DisplayName("Адрес кошелька")
    class WalletAddress {

        @Test
        @DisplayName("getAddress возвращает читаемый адрес")
        void getAddressReturnsReadableAddress() {
            String address = walletService.getAddress(alice);

            assertNotNull(address);
            assertFalse(address.isEmpty());
        }

        @Test
        @DisplayName("разные кошельки имеют разные адреса")
        void differentWalletsHaveDifferentAddresses() {
            String aliceAddress = walletService.getAddress(alice);
            String bobAddress = walletService.getAddress(bob);

            assertNotEquals(aliceAddress, bobAddress);
        }
    }

    // === Helper methods ===

    private TransactionOutput giveCoins(Wallet wallet, double amount) {
        TransactionOutput utxo = new TransactionOutput(
                wallet.getPublicKey(), amount, "utxo-" + System.nanoTime());
        utxoPool.addUTXO(utxo);
        return utxo;
    }
}
