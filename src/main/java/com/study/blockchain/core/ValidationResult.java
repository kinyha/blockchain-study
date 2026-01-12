package com.study.blockchain.core;

/**
 * Результат валидации blockchain.
 *
 * Этот класс уже реализован — используй его в Blockchain.validateDetailed()
 *
 * Пример использования:
 *   return ValidationResult.success();                           // цепочка валидна
 *   return ValidationResult.failure(2, "Block 2 hash mismatch"); // ошибка в блоке 2
 */
public class ValidationResult {

    private final boolean valid;
    private final int invalidBlockIndex;
    private final String errorMessage;

    private ValidationResult(boolean valid, int invalidBlockIndex, String errorMessage) {
        this.valid = valid;
        this.invalidBlockIndex = invalidBlockIndex;
        this.errorMessage = errorMessage;
    }

    /**
     * Создаёт успешный результат валидации.
     */
    public static ValidationResult success() {
        return new ValidationResult(true, -1, null);
    }

    /**
     * Создаёт результат с ошибкой.
     *
     * @param blockIndex индекс невалидного блока
     * @param message    описание ошибки
     */
    public static ValidationResult failure(int blockIndex, String message) {
        return new ValidationResult(false, blockIndex, message);
    }

    public boolean isValid() {
        return valid;
    }

    public int getInvalidBlockIndex() {
        return invalidBlockIndex;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        if (valid) {
            return "ValidationResult{valid=true}";
        }
        return "ValidationResult{" +
                "valid=false" +
                ", invalidBlockIndex=" + invalidBlockIndex +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
