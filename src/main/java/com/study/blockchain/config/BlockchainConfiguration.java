package com.study.blockchain.config;

import com.study.blockchain.service.BlockchainService;
import com.study.blockchain.service.OrchestratorService;
import com.study.blockchain.wallet.Wallet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Spring configuration for blockchain components.
 */
@Configuration
public class BlockchainConfiguration {

    @Value("${blockchain.difficulty:4}")
    private int difficulty;

    /**
     * Creates the main BlockchainService with configured difficulty.
     * Not created for orchestrator profile.
     */
    @Bean
    @Profile("!orchestrator")
    public BlockchainService blockchainService() {
        return new BlockchainService(difficulty);
    }

    /**
     * Creates the node's wallet.
     * Each node has its own wallet for receiving mining rewards and sending transactions.
     * Not created for orchestrator profile.
     */
    @Bean
    @Profile("!orchestrator")
    public Wallet nodeWallet() {
        return new Wallet();
    }

    /**
     * Creates the OrchestratorService.
     * Only created for orchestrator profile.
     */
    @Bean
    @Profile("orchestrator")
    public OrchestratorService orchestratorService() {
        return new OrchestratorService();
    }
}
