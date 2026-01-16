package com.study.blockchain.api;

import com.study.blockchain.api.dto.NodeStatusDto;
import com.study.blockchain.service.BlockchainService;
import com.study.blockchain.wallet.Wallet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST controller for node operations.
 */
@RestController
@RequestMapping("/api/node")
@Profile("!orchestrator")
public class NodeController {

    private final BlockchainService blockchainService;
    private final Wallet nodeWallet;
    private final Set<String> peers = ConcurrentHashMap.newKeySet();

    @Value("${node.id:node-1}")
    private String nodeId;

    public NodeController(BlockchainService blockchainService, Wallet nodeWallet) {
        this.blockchainService = blockchainService;
        this.nodeWallet = nodeWallet;
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    /**
     * Get node status.
     */
    @GetMapping("/status")
    public NodeStatusDto getStatus() {
        return new NodeStatusDto(
                nodeId,
                blockchainService.getChainHeight(),
                blockchainService.getBlockchainSize(),
                blockchainService.getPendingTransactionsCount(),
                false, // Will be integrated with MiningController
                new ArrayList<>(peers)
        );
    }

    /**
     * Get list of known peers.
     */
    @GetMapping("/peers")
    public List<String> getPeers() {
        return new ArrayList<>(peers);
    }

    /**
     * Add a peer to the known peers list.
     */
    @PostMapping("/peers")
    public String addPeer(@RequestBody String peerUrl) {
        peers.add(peerUrl.trim());
        return "Peer added: " + peerUrl;
    }

    /**
     * Remove a peer from the known peers list.
     */
    @DeleteMapping("/peers")
    public String removePeer(@RequestBody String peerUrl) {
        peers.remove(peerUrl.trim());
        return "Peer removed: " + peerUrl;
    }

    /**
     * Get this node's wallet address.
     */
    @GetMapping("/address")
    public String getNodeAddress() {
        return blockchainService.getWalletAddress(nodeWallet);
    }
}
