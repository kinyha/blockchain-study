package com.study.blockchain.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Утилита для криптографического хэширования.
 *
 * Использует алгоритм SHA-256, который:
 * - Генерирует 256-битный (64 символа hex) hash
 * - Является однонаправленным (нельзя восстановить исходные данные)
 * - Детерминированным (одинаковый вход = одинаковый выход)
 * - Лавинным (малое изменение входа = полное изменение выхода)
 *
 * Подробнее о хэшировании — в уроке 2.
 */
public final class HashUtil {

    private HashUtil() {
        // Utility class, не должен инстанцироваться
    }

    /**
     * Вычисляет SHA-256 hash строки.
     *
     * @param input входная строка
     * @return 64-символьный hex-encoded hash
     * @throws RuntimeException если алгоритм SHA-256 недоступен
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 должен быть доступен во всех JVM
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Конвертирует массив байтов в hex-строку.
     *
     * @param bytes массив байтов
     * @return hex-представление (lowercase)
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
