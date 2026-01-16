package com.study.blockchain.api.dto;

/**
 * DTO for node information in orchestrator view.
 */
public record NodeInfoDto(
        String nodeId,
        String url,
        String role,
        int chainHeight,
        int pendingTransactions,
        boolean mining,
        boolean active,
        String walletAddress,
        double balance
) {}
