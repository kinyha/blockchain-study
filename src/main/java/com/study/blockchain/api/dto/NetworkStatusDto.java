package com.study.blockchain.api.dto;

import java.util.List;

/**
 * DTO for overall network status.
 */
public record NetworkStatusDto(
        int totalNodes,
        int activeNodes,
        int maxChainHeight,
        int totalPendingTransactions,
        int miningNodes,
        List<NodeInfoDto> nodes
) {}
