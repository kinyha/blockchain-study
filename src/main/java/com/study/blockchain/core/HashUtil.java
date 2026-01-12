package com.study.blockchain.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Утилита для криптографического хэширования.
 *
 * ЗАДАНИЕ (Урок 2):
 * Реализуй метод sha256() согласно инструкциям в lessons/02-hashing.md
 *
 * После реализации запусти тесты:
 *   ./gradlew test --tests HashUtilTest
 */
public final class HashUtil {

    private HashUtil() {
        // Utility class
    }

    /**
     * TODO: Вычисляет SHA-256 hash строки.
     *
     * Алгоритм:
     * 1. Получи MessageDigest для "SHA-256"
     * 2. Преобразуй input в байты (используй StandardCharsets.UTF_8)
     * 3. Вычисли hash через digest()
     * 4. Преобразуй байты в hex-строку через bytesToHex()
     *
     * @param input входная строка
     * @return 64-символьный hex-encoded hash
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Конвертирует массив байтов в hex-строку.
     * Этот метод уже реализован — используй его в sha256().
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
