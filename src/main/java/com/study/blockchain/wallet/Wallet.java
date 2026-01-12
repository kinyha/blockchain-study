package com.study.blockchain.wallet;

import com.study.blockchain.core.HashUtil;
import java.security.*;
import java.util.Base64;

/**
 * Криптографический кошелёк с ключевой парой ECDSA.
 * Задание: lessons/06-wallets.md
 */
public class Wallet {

    private PrivateKey privateKey;
    private PublicKey publicKey;

    public Wallet() {
        // TODO: Сгенерируй ключевую пару ECDSA
        // KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        // keyGen.initialize(256);
        // KeyPair pair = keyGen.generateKeyPair();
        throw new UnsupportedOperationException("Реализуй конструктор Wallet");
    }

    public byte[] sign(byte[] data) {
        // TODO: Подпиши данные приватным ключом
        // Signature sig = Signature.getInstance("SHA256withECDSA");
        // sig.initSign(privateKey);
        // sig.update(data);
        // return sig.sign();
        throw new UnsupportedOperationException("Реализуй sign");
    }

    public static boolean verify(PublicKey publicKey, byte[] data, byte[] signature) {
        // TODO: Проверь подпись публичным ключом
        // Signature sig = Signature.getInstance("SHA256withECDSA");
        // sig.initVerify(publicKey);
        // sig.update(data);
        // return sig.verify(signature);
        throw new UnsupportedOperationException("Реализуй verify");
    }

    public String getAddress() {
        // TODO: Верни sha256 от publicKey.getEncoded()
        throw new UnsupportedOperationException("Реализуй getAddress");
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }
}
