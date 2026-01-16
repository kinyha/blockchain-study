package com.study.blockchain.api;

import com.study.blockchain.api.dto.BlockDto;
import com.study.blockchain.core.Block;
import com.study.blockchain.service.BlockchainService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for blockchain block operations.
 */
@RestController
@RequestMapping("/api/blocks")
@Profile("!orchestrator")
public class BlockController {

    private final BlockchainService blockchainService;

    public BlockController(BlockchainService blockchainService) {
        this.blockchainService = blockchainService;
    }

    /**
     * Get all blocks in the chain.
     */
    @GetMapping
    public List<BlockDto> getAllBlocks() {
        return blockchainService.getBlocks().stream()
                .map(BlockDto::from)
                .toList();
    }

    /**
     * Get the latest block.
     */
    @GetMapping("/latest")
    public BlockDto getLatestBlock() {
        return BlockDto.from(blockchainService.getLatestBlock());
    }

    /**
     * Get current chain height.
     */
    @GetMapping("/height")
    public int getHeight() {
        return blockchainService.getChainHeight();
    }

    /**
     * Get a block by index.
     */
    @GetMapping("/{index}")
    public ResponseEntity<BlockDto> getBlock(@PathVariable int index) {
        Block block = blockchainService.getBlock(index);
        if (block == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(BlockDto.from(block));
    }

    /**
     * Receive a block from a peer.
     */
    @PostMapping
    public ResponseEntity<String> receiveBlock(@RequestBody BlockDto blockDto) {
        // Convert DTO to Block (simplified - in production would need full validation)
        Block block = new Block(
                blockDto.index(),
                blockDto.timestamp(),
                blockDto.data(),
                blockDto.previousHash()
        );
        block.setNonce(blockDto.nonce());
        block.updateHash();

        var result = blockchainService.receiveBlock(block);
        if (result.isAccepted()) {
            return ResponseEntity.ok("Block accepted");
        } else {
            return ResponseEntity.badRequest().body(result.getReason());
        }
    }
}
