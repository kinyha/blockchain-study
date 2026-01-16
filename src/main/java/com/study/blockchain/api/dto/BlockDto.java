package com.study.blockchain.api.dto;

import com.study.blockchain.core.Block;

/**
 * DTO for Block data in REST API responses.
 */
public record BlockDto(
        int index,
        long timestamp,
        String data,
        String previousHash,
        String hash,
        long nonce
) {
    public static BlockDto from(Block block) {
        return new BlockDto(
                block.getIndex(),
                block.getTimestamp(),
                block.getData(),
                block.getPreviousHash(),
                block.getHash(),
                block.getNonce()
        );
    }
}
