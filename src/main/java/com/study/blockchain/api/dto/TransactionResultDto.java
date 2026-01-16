package com.study.blockchain.api.dto;

/**
 * DTO for transaction creation result.
 */
public record TransactionResultDto(
        boolean success,
        String transactionId,
        String error
) {
    public static TransactionResultDto success(String transactionId) {
        return new TransactionResultDto(true, transactionId, null);
    }

    public static TransactionResultDto failure(String error) {
        return new TransactionResultDto(false, null, error);
    }
}
