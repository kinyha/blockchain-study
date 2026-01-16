package com.study.blockchain.api.dto;

/**
 * DTO for mining status.
 */
public record MiningStatusDto(
        boolean active,
        int minedBlocks,
        String minerAddress
) {}
