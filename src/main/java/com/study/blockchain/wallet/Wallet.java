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
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(git256);
            KeyPair pair = keyGen.generateKeyPair();
            this.privateKey = pair.getPrivate();
            this.publicKey = pair.getPublic();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("EC algorith now available", e);
        }

    }

    public static boolean verify(PublicKey publicKey, byte[] data, byte[] signature) {
        try {
            Signature sig = Signature.getInstance("SHA256withECDSA");
            sig.initVerify(publicKey);
            sig.update(data);
            return sig.verify(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            return false;
        }
    }

    public byte[] sign(byte[] data) {
        try {
            Signature sig = Signature.getInstance("SHA256withECDSA");
            sig.initSign(privateKey);
            sig.update(data);
            return sig.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new RuntimeException("Signing failed", e);
        }
    }

    public String getAddress() {
        return HashUtil.sha256(Base64.getEncoder().encodeToString(publicKey.getEncoded()));
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }
}
