package com.study.blockchain.api.dto;

import java.util.List;

/**
 * DTO for node status information.
 */
public record NodeStatusDto(
        String nodeId,
        int chainHeight,
        int blockCount,
        int pendingTransactions,
        boolean mining,
        List<String> peers
) {}
