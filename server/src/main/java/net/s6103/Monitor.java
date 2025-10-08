package net.s6103;

import java.time.Instant;
import java.time.Duration;

/**
 * Monitor of appointments.
 */
public class Monitor {
    private final Instant beginInstant;
    private final Duration monitorInterval;
    private final ClientInfo client;

    public Monitor(ClientInfo client, Duration monitorInterval) {
        this.beginInstant = Instant.now();
        if (monitorInterval.toSeconds() > 60 * 60 * 24 * 7) {
            throw new IllegalArgumentException("Monitor interval too long");
        }
        this.monitorInterval = monitorInterval;
        this.client = client;
    }

    public boolean isExpired() {
        Instant end = beginInstant.plus(monitorInterval);
        return Instant.now().isAfter(end);
    }

    public ClientInfo getClient() {
        return client;
    }
}

