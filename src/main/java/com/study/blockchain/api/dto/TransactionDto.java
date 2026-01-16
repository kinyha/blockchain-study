package com.study.blockchain.api.dto;

/**
 * DTO for creating a new transaction via REST API.
 */
public record TransactionDto(
        String recipientAddress,
        double amount
) {}
