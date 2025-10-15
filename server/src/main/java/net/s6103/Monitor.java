package net.s6103;

import java.time.*;

/**
 * Monitor of appointments.
 */
public class Monitor {
    private final Instant beginInstant;
    private final Duration monitorInterval;
    private final ClientInfo client;

    public Monitor(ClientInfo client, Duration monitorInterval) {
        // 获取一个基于当前时间的UTC时间戳
        ZoneId localZone = ZoneId.systemDefault(); // 比如 Asia/Singapore
        ZonedDateTime localNow = ZonedDateTime.now(localZone);
        LocalTime localTime = localNow.toLocalTime();
        LocalDate localDate = localNow.toLocalDate();
        ZonedDateTime utcSameClock = ZonedDateTime.of(localDate, localTime, ZoneOffset.UTC);
        this.beginInstant = utcSameClock.toInstant();
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
    public Instant getBeginInstant() {
        return beginInstant;
    }
    public Duration getMonitorInterval() {
        return monitorInterval;
    }
}

