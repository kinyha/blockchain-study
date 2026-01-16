package com.study.blockchain.api.dto;

import java.util.List;

/**
 * DTO for wallet information.
 */
public record WalletInfoDto(
        String address,
        double balance,
        List<UtxoDto> utxos
) {}
