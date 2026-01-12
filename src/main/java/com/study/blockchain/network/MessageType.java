package com.study.blockchain.network;

/**
 * Типы сообщений P2P протокола.
 * Задание: lessons/10-network.md
 */
public enum MessageType {
    HANDSHAKE,
    NEW_BLOCK,
    NEW_TRANSACTION,
    GET_BLOCKS,
    BLOCKS,
    GET_PEERS,
    PEERS
}
