package com.study.blockchain.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для класса HashUtil.
 * Демонстрируют свойства криптографических хэш-функций.
 */
class HashUtilTest {

    @Test
    @DisplayName("SHA-256 возвращает hash длиной 64 символа")
    void sha256_shouldReturn64CharacterHash() {
        // SHA-256 = 256 бит = 32 байта = 64 hex символа
        String hash = HashUtil.sha256("test");

        assertEquals(64, hash.length());
    }

    @Test
    @DisplayName("SHA-256 возвращает только hex-символы")
    void sha256_shouldReturnOnlyHexCharacters() {
        String hash = HashUtil.sha256("test");

        assertTrue(hash.matches("[0-9a-f]+"));
    }

    @Test
    @DisplayName("Детерминированность: одинаковый вход = одинаковый выход")
    void sha256_sameInput_shouldReturnSameHash() {
        String input = "Hello, Blockchain!";

        String hash1 = HashUtil.sha256(input);
        String hash2 = HashUtil.sha256(input);

        assertEquals(hash1, hash2);
    }

    @Test
    @DisplayName("Лавинный эффект: малое изменение = совершенно другой hash")
    void sha256_slightChange_shouldReturnCompletelyDifferentHash() {
        String hash1 = HashUtil.sha256("Hello");
        String hash2 = HashUtil.sha256("hello");  // только регистр первой буквы

        assertNotEquals(hash1, hash2);

        // Проверяем, что hash-и отличаются существенно (не только несколькими символами)
        int differentChars = 0;
        for (int i = 0; i < hash1.length(); i++) {
            if (hash1.charAt(i) != hash2.charAt(i)) {
                differentChars++;
            }
        }
        // Ожидаем, что большинство символов будут разными (лавинный эффект)
        assertTrue(differentChars > 30, "Expected avalanche effect: most characters should differ");
    }

    @Test
    @DisplayName("Разные входы дают разные hash-и")
    void sha256_differentInputs_shouldReturnDifferentHashes() {
        String hash1 = HashUtil.sha256("input1");
        String hash2 = HashUtil.sha256("input2");
        String hash3 = HashUtil.sha256("input3");

        assertNotEquals(hash1, hash2);
        assertNotEquals(hash2, hash3);
        assertNotEquals(hash1, hash3);
    }

    @Test
    @DisplayName("Пустая строка имеет валидный hash")
    void sha256_emptyString_shouldReturnValidHash() {
        String hash = HashUtil.sha256("");

        assertNotNull(hash);
        assertEquals(64, hash.length());
        // Известный SHA-256 hash пустой строки
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
    }

    @Test
    @DisplayName("Длинная строка хэшируется корректно")
    void sha256_longInput_shouldReturnValidHash() {
        // Создаём длинную строку (10000 символов)
        String longInput = "a".repeat(10000);

        String hash = HashUtil.sha256(longInput);

        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    @Test
    @DisplayName("Unicode символы хэшируются корректно")
    void sha256_unicodeInput_shouldReturnValidHash() {
        String hash = HashUtil.sha256("Привет, мир! 你好世界");

        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    @Test
    @DisplayName("Известный тест-вектор SHA-256")
    void sha256_knownTestVector_shouldMatchExpected() {
        // Известные тест-векторы для проверки корректности реализации
        assertEquals(
                "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
                HashUtil.sha256("test")
        );

        assertEquals(
                "185f8db32271fe25f561a6fc938b2e264306ec304eda518007d1764826381969",
                HashUtil.sha256("Hello")
        );
    }

    @Test
    @DisplayName("Добавление пробела меняет hash")
    void sha256_withTrailingSpace_shouldReturnDifferentHash() {
        String hash1 = HashUtil.sha256("test");
        String hash2 = HashUtil.sha256("test ");  // с пробелом на конце

        assertNotEquals(hash1, hash2);
    }
}
