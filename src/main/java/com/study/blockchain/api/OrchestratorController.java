package com.study.blockchain.api;

import com.study.blockchain.api.dto.NetworkStatusDto;
import com.study.blockchain.api.dto.NodeInfoDto;
import com.study.blockchain.api.dto.ScenarioResultDto;
import com.study.blockchain.service.OrchestratorService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for orchestrator operations.
 *
 * This controller is only active when running with the 'orchestrator' profile.
 * It provides endpoints for:
 * - Network monitoring
 * - Node management
 * - Scenario execution (for testing/demos)
 */
@RestController
@RequestMapping("/api/orchestrator")
@Profile("orchestrator")
@CrossOrigin(origins = "*")
public class OrchestratorController {

    private final OrchestratorService orchestratorService;

    public OrchestratorController(OrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    // === Network Monitoring ===

    /**
     * Get overall network status.
     * Returns aggregated information from all registered nodes.
     */
    @GetMapping("/network")
    public NetworkStatusDto getNetworkStatus() {
        return orchestratorService.getNetworkStatus();
    }

    /**
     * Get detailed list of all nodes.
     */
    @GetMapping("/nodes")
    public List<NodeInfoDto> getNodes() {
        return orchestratorService.getNodes();
    }

    /**
     * Get list of registered node IDs.
     */
    @GetMapping("/nodes/ids")
    public List<String> getNodeIds() {
        return orchestratorService.getRegisteredNodeIds();
    }

    // === Node Registration ===

    /**
     * Register a new node with the orchestrator.
     *
     * Request body:
     * {
     *   "nodeId": "miner-0",
     *   "url": "http://miner-service:8080",
     *   "role": "miner"
     * }
     */
    @PostMapping("/nodes/register")
    public String registerNode(@RequestBody Map<String, String> request) {
        String nodeId = request.get("nodeId");
        String url = request.get("url");
        String role = request.getOrDefault("role", "full");

        if (nodeId == null || url == null) {
            return "Error: nodeId and url are required";
        }

        orchestratorService.registerNode(nodeId, url, role);
        return "Node registered: " + nodeId;
    }

    /**
     * Unregister a node from the orchestrator.
     */
    @DeleteMapping("/nodes/{nodeId}")
    public String unregisterNode(@PathVariable String nodeId) {
        orchestratorService.unregisterNode(nodeId);
        return "Node unregistered: " + nodeId;
    }

    // === Scenario Execution ===

    /**
     * Run a transaction burst scenario.
     * Creates multiple transactions to simulate network load.
     *
     * @param count Number of transactions to create (default: 10)
     */
    @PostMapping("/scenario/transaction-burst")
    public ScenarioResultDto runTransactionBurst(
            @RequestParam(defaultValue = "10") int count) {
        return orchestratorService.runTransactionBurst(count);
    }

    /**
     * Start mining on all miner nodes.
     */
    @PostMapping("/scenario/start-miners")
    public ScenarioResultDto startMiners() {
        return orchestratorService.startAllMiners();
    }

    /**
     * Stop mining on all miner nodes.
     */
    @PostMapping("/scenario/stop-miners")
    public ScenarioResultDto stopMiners() {
        return orchestratorService.stopAllMiners();
    }

    /**
     * Trigger synchronization on all nodes.
     */
    @PostMapping("/scenario/sync-all")
    public ScenarioResultDto syncAllNodes() {
        return orchestratorService.syncAllNodes();
    }

    // === Health ===

    /**
     * Health check for orchestrator.
     */
    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
