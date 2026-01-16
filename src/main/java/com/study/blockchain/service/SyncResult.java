package com.study.blockchain.service;

/**
 * Result of a synchronization operation.
 */
public class SyncResult {

    private final boolean accepted;
    private final String reason;

    private SyncResult(boolean accepted, String reason) {
        this.accepted = accepted;
        this.reason = reason;
    }

    public static SyncResult accepted() {
        return new SyncResult(true, null);
    }

    public static SyncResult rejected(String reason) {
        return new SyncResult(false, reason);
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getReason() {
        return reason;
    }
}
