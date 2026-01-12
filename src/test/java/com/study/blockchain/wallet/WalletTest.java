package com.study.blockchain.wallet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WalletTest {

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = new Wallet();
    }

    @Test
    @DisplayName("генерирует приватный ключ")
    void generatesPrivateKey() {
        assertNotNull(wallet.getPrivateKey());
    }

    @Test
    @DisplayName("генерирует публичный ключ")
    void generatesPublicKey() {
        assertNotNull(wallet.getPublicKey());
    }

    @Test
    @DisplayName("каждый кошелёк имеет уникальные ключи")
    void uniqueKeys() {
        Wallet wallet2 = new Wallet();

        assertNotEquals(
            wallet.getPrivateKey().getEncoded(),
            wallet2.getPrivateKey().getEncoded()
        );
        assertNotEquals(
            wallet.getPublicKey().getEncoded(),
            wallet2.getPublicKey().getEncoded()
        );
    }

    @Test
    @DisplayName("адрес имеет формат SHA-256")
    void addressIsSha256() {
        String address = wallet.getAddress();

        assertNotNull(address);
        assertEquals(64, address.length());
        assertTrue(address.matches("[a-f0-9]+"));
    }

    @Test
    @DisplayName("каждый кошелёк имеет уникальный адрес")
    void uniqueAddress() {
        Wallet wallet2 = new Wallet();

        assertNotEquals(wallet.getAddress(), wallet2.getAddress());
    }

    @Test
    @DisplayName("подписывает данные")
    void signsData() {
        byte[] data = "test message".getBytes();
        byte[] signature = wallet.sign(data);

        assertNotNull(signature);
        assertTrue(signature.length > 0);
    }

    @Test
    @DisplayName("подпись верифицируется публичным ключом")
    void verifyValidSignature() {
        byte[] data = "test message".getBytes();
        byte[] signature = wallet.sign(data);

        assertTrue(Wallet.verify(wallet.getPublicKey(), data, signature));
    }

    @Test
    @DisplayName("подпись не проходит с другим публичным ключом")
    void verifyWithWrongKey() {
        Wallet wallet2 = new Wallet();
        byte[] data = "test message".getBytes();
        byte[] signature = wallet.sign(data);

        assertFalse(Wallet.verify(wallet2.getPublicKey(), data, signature));
    }

    @Test
    @DisplayName("подпись не проходит с изменёнными данными")
    void verifyWithTamperedData() {
        byte[] data = "test message".getBytes();
        byte[] signature = wallet.sign(data);

        byte[] tamperedData = "tampered message".getBytes();
        assertFalse(Wallet.verify(wallet.getPublicKey(), tamperedData, signature));
    }

    @Test
    @DisplayName("разные данные дают разные подписи")
    void differentDataDifferentSignatures() {
        byte[] data1 = "message one".getBytes();
        byte[] data2 = "message two".getBytes();

        byte[] sig1 = wallet.sign(data1);
        byte[] sig2 = wallet.sign(data2);

        assertFalse(java.util.Arrays.equals(sig1, sig2));
    }
}
