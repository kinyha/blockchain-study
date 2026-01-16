package com.study.blockchain.api;

import com.study.blockchain.api.dto.BlockDto;
import com.study.blockchain.api.dto.MiningStatusDto;
import com.study.blockchain.core.Block;
import com.study.blockchain.service.BlockchainService;
import com.study.blockchain.wallet.Wallet;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * REST controller for mining operations.
 */
@RestController
@RequestMapping("/api/mining")
@Profile("!orchestrator")
public class MiningController {

    private final BlockchainService blockchainService;
    private final Wallet nodeWallet;

    private final AtomicBoolean miningActive = new AtomicBoolean(false);
    private final AtomicInteger minedBlocks = new AtomicInteger(0);
    private Thread miningThread;

    public MiningController(BlockchainService blockchainService, Wallet nodeWallet) {
        this.blockchainService = blockchainService;
        this.nodeWallet = nodeWallet;
    }

    /**
     * Get mining status.
     */
    @GetMapping("/status")
    public MiningStatusDto getStatus() {
        return new MiningStatusDto(
                miningActive.get(),
                minedBlocks.get(),
                blockchainService.getWalletAddress(nodeWallet)
        );
    }

    /**
     * Mine a single block manually.
     */
    @PostMapping("/mine")
    public ResponseEntity<BlockDto> mineBlock() {
        if (miningActive.get()) {
            return ResponseEntity.badRequest().build();
        }

        Block block = blockchainService.mineBlock(nodeWallet);
        minedBlocks.incrementAndGet();
        return ResponseEntity.ok(BlockDto.from(block));
    }

    /**
     * Start continuous mining.
     */
    @PostMapping("/start")
    public ResponseEntity<String> startMining() {
        if (miningActive.get()) {
            return ResponseEntity.ok("Mining already active");
        }

        miningActive.set(true);
        miningThread = new Thread(() -> {
            while (miningActive.get()) {
                try {
                    blockchainService.mineBlock(nodeWallet);
                    minedBlocks.incrementAndGet();
                    // Small delay to prevent overwhelming the system
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // Log error but continue mining
                    System.err.println("Mining error: " + e.getMessage());
                }
            }
        }, "mining-thread");
        miningThread.start();

        return ResponseEntity.ok("Mining started");
    }

    /**
     * Stop continuous mining.
     */
    @PostMapping("/stop")
    public ResponseEntity<String> stopMining() {
        if (!miningActive.get()) {
            return ResponseEntity.ok("Mining not active");
        }

        miningActive.set(false);
        if (miningThread != null) {
            miningThread.interrupt();
        }

        return ResponseEntity.ok("Mining stopped");
    }
}
