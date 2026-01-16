package com.study.blockchain.api.dto;

import com.study.blockchain.transaction.TransactionOutput;

/**
 * DTO for UTXO data.
 */
public record UtxoDto(
        String id,
        double amount,
        String parentTransactionId
) {
    public static UtxoDto from(TransactionOutput utxo) {
        return new UtxoDto(
                utxo.getId(),
                utxo.getAmount(),
                utxo.getParentTransactionId()
        );
    }
}
