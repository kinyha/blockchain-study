package com.study.blockchain.core;

/**
 * Результат валидации blockchain.
 *
 * Содержит информацию о том, валидна ли цепочка,
 * и если нет — какой блок невалиден и почему.
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

    /**
     * Возвращает true, если цепочка валидна.
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Возвращает индекс первого невалидного блока.
     * Возвращает -1, если цепочка валидна.
     */
    public int getInvalidBlockIndex() {
        return invalidBlockIndex;
    }

    /**
     * Возвращает описание ошибки.
     * Возвращает null, если цепочка валидна.
     */
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
