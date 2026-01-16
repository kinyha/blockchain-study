package com.study.blockchain.service;

import com.study.blockchain.api.dto.NetworkStatusDto;
import com.study.blockchain.api.dto.NodeInfoDto;
import com.study.blockchain.api.dto.NodeStatusDto;
import com.study.blockchain.api.dto.ScenarioResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Service for orchestrating and monitoring the blockchain network.
 *
 * The orchestrator:
 * - Discovers and tracks network nodes
 * - Collects network-wide statistics
 * - Executes test scenarios (fork simulation, transaction bursts, etc.)
 *
 * Note: This service is created only when 'orchestrator' profile is active.
 * See BlockchainConfiguration for bean definition.
 */
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);
    private static final int REQUEST_TIMEOUT_MS = 5000;

    private final RestTemplate restTemplate;
    private final ExecutorService executor;
    private final Map<String, NodeRegistration> registeredNodes = new ConcurrentHashMap<>();

    public OrchestratorService() {
        this.restTemplate = new RestTemplate();
        this.executor = Executors.newFixedThreadPool(10);
    }

    /**
     * Registers a node with the orchestrator.
     *
     * @param nodeId   Node identifier
     * @param url      Node base URL (e.g., http://miner-0:8080)
     * @param role     Node role (miner, wallet, full)
     */
    public void registerNode(String nodeId, String url, String role) {
        registeredNodes.put(nodeId, new NodeRegistration(nodeId, url, role));
        log.info("Node registered: {} at {} with role {}", nodeId, url, role);
    }

    /**
     * Unregisters a node from the orchestrator.
     *
     * @param nodeId Node identifier
     */
    public void unregisterNode(String nodeId) {
        registeredNodes.remove(nodeId);
        log.info("Node unregistered: {}", nodeId);
    }

    /**
     * Gets list of registered node IDs.
     *
     * @return List of node IDs
     */
    public List<String> getRegisteredNodeIds() {
        return new ArrayList<>(registeredNodes.keySet());
    }

    /**
     * Gets the current network status by querying all registered nodes.
     *
     * @return Network status with aggregated information
     */
    public NetworkStatusDto getNetworkStatus() {
        List<NodeInfoDto> nodeInfos = registeredNodes.values().parallelStream()
                .map(this::fetchNodeInfo)
                .collect(Collectors.toList());

        int activeNodes = (int) nodeInfos.stream().filter(NodeInfoDto::active).count();
        int maxHeight = nodeInfos.stream().mapToInt(NodeInfoDto::chainHeight).max().orElse(0);
        int totalPending = nodeInfos.stream().mapToInt(NodeInfoDto::pendingTransactions).sum();
        int miningCount = (int) nodeInfos.stream().filter(NodeInfoDto::mining).count();

        return new NetworkStatusDto(
                registeredNodes.size(),
                activeNodes,
                maxHeight,
                totalPending,
                miningCount,
                nodeInfos
        );
    }

    /**
     * Gets detailed information about all registered nodes.
     *
     * @return List of node info DTOs
     */
    public List<NodeInfoDto> getNodes() {
        return registeredNodes.values().parallelStream()
                .map(this::fetchNodeInfo)
                .collect(Collectors.toList());
    }

    /**
     * Fetches info from a single node.
     */
    private NodeInfoDto fetchNodeInfo(NodeRegistration reg) {
        try {
            String statusUrl = reg.url() + "/api/node/status";
            NodeStatusDto status = restTemplate.getForObject(statusUrl, NodeStatusDto.class);

            String addressUrl = reg.url() + "/api/node/address";
            String address = restTemplate.getForObject(addressUrl, String.class);

            double balance = 0;
            try {
                String balanceUrl = reg.url() + "/api/wallet/balance";
                Double balanceResult = restTemplate.getForObject(balanceUrl, Double.class);
                balance = balanceResult != null ? balanceResult : 0;
            } catch (Exception e) {
                // Balance might not be available
            }

            return new NodeInfoDto(
                    reg.nodeId(),
                    reg.url(),
                    reg.role(),
                    status != null ? status.chainHeight() : 0,
                    status != null ? status.pendingTransactions() : 0,
                    status != null && status.mining(),
                    true,
                    address,
                    balance
            );
        } catch (Exception e) {
            log.warn("Failed to fetch info from node {}: {}", reg.nodeId(), e.getMessage());
            return new NodeInfoDto(
                    reg.nodeId(),
                    reg.url(),
                    reg.role(),
                    0, 0, false, false, "", 0
            );
        }
    }

    // === Scenario Execution ===

    /**
     * Triggers a transaction burst - sends multiple transactions from wallet nodes.
     *
     * @param count Number of transactions to send
     * @return Scenario result
     */
    public ScenarioResultDto runTransactionBurst(int count) {
        long start = System.currentTimeMillis();

        try {
            List<NodeRegistration> walletNodes = registeredNodes.values().stream()
                    .filter(n -> "wallet".equals(n.role()) || "full".equals(n.role()))
                    .collect(Collectors.toList());

            if (walletNodes.isEmpty()) {
                return new ScenarioResultDto("transaction-burst", false,
                        "No wallet nodes available", System.currentTimeMillis() - start);
            }

            int successCount = 0;
            for (int i = 0; i < count; i++) {
                NodeRegistration node = walletNodes.get(i % walletNodes.size());
                try {
                    // Get a random recipient from other nodes
                    Optional<String> recipientAddress = getRandomRecipientAddress(node.nodeId());
                    if (recipientAddress.isPresent()) {
                        Map<String, Object> txRequest = new HashMap<>();
                        txRequest.put("recipientAddress", recipientAddress.get());
                        txRequest.put("amount", 0.1);

                        restTemplate.postForObject(
                                node.url() + "/api/transactions",
                                txRequest,
                                Object.class
                        );
                        successCount++;
                    }
                } catch (Exception e) {
                    log.warn("Transaction failed from {}: {}", node.nodeId(), e.getMessage());
                }
            }

            return new ScenarioResultDto("transaction-burst", true,
                    String.format("Created %d/%d transactions", successCount, count),
                    System.currentTimeMillis() - start);

        } catch (Exception e) {
            return new ScenarioResultDto("transaction-burst", false,
                    "Error: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    /**
     * Triggers mining on all miner nodes.
     *
     * @return Scenario result
     */
    public ScenarioResultDto startAllMiners() {
        long start = System.currentTimeMillis();

        try {
            List<NodeRegistration> minerNodes = registeredNodes.values().stream()
                    .filter(n -> "miner".equals(n.role()) || "full".equals(n.role()))
                    .collect(Collectors.toList());

            int successCount = 0;
            for (NodeRegistration node : minerNodes) {
                try {
                    restTemplate.postForObject(node.url() + "/api/mining/start", null, String.class);
                    successCount++;
                } catch (Exception e) {
                    log.warn("Failed to start mining on {}: {}", node.nodeId(), e.getMessage());
                }
            }

            return new ScenarioResultDto("start-miners", true,
                    String.format("Started mining on %d/%d nodes", successCount, minerNodes.size()),
                    System.currentTimeMillis() - start);

        } catch (Exception e) {
            return new ScenarioResultDto("start-miners", false,
                    "Error: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    /**
     * Stops mining on all miner nodes.
     *
     * @return Scenario result
     */
    public ScenarioResultDto stopAllMiners() {
        long start = System.currentTimeMillis();

        try {
            List<NodeRegistration> minerNodes = registeredNodes.values().stream()
                    .filter(n -> "miner".equals(n.role()) || "full".equals(n.role()))
                    .collect(Collectors.toList());

            int successCount = 0;
            for (NodeRegistration node : minerNodes) {
                try {
                    restTemplate.postForObject(node.url() + "/api/mining/stop", null, String.class);
                    successCount++;
                } catch (Exception e) {
                    log.warn("Failed to stop mining on {}: {}", node.nodeId(), e.getMessage());
                }
            }

            return new ScenarioResultDto("stop-miners", true,
                    String.format("Stopped mining on %d/%d nodes", successCount, minerNodes.size()),
                    System.currentTimeMillis() - start);

        } catch (Exception e) {
            return new ScenarioResultDto("stop-miners", false,
                    "Error: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    /**
     * Triggers sync on all nodes.
     *
     * @return Scenario result
     */
    public ScenarioResultDto syncAllNodes() {
        long start = System.currentTimeMillis();

        try {
            int successCount = 0;
            for (NodeRegistration node : registeredNodes.values()) {
                try {
                    restTemplate.postForObject(node.url() + "/api/node/sync", null, String.class);
                    successCount++;
                } catch (Exception e) {
                    log.warn("Failed to trigger sync on {}: {}", node.nodeId(), e.getMessage());
                }
            }

            return new ScenarioResultDto("sync-all", true,
                    String.format("Triggered sync on %d/%d nodes", successCount, registeredNodes.size()),
                    System.currentTimeMillis() - start);

        } catch (Exception e) {
            return new ScenarioResultDto("sync-all", false,
                    "Error: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private Optional<String> getRandomRecipientAddress(String excludeNodeId) {
        return registeredNodes.values().stream()
                .filter(n -> !n.nodeId().equals(excludeNodeId))
                .findAny()
                .map(n -> {
                    try {
                        return restTemplate.getForObject(n.url() + "/api/node/address", String.class);
                    } catch (Exception e) {
                        return null;
                    }
                });
    }

    /**
     * Internal record for node registration data.
     */
    private record NodeRegistration(String nodeId, String url, String role) {}
}
