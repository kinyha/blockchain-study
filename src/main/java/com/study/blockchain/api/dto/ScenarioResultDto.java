package com.study.blockchain.api.dto;

/**
 * DTO for scenario execution result.
 */
public record ScenarioResultDto(
        String scenario,
        boolean success,
        String message,
        long durationMs
) {}
