package net.s6103;

import java.net.InetAddress;
import java.time.Instant;

public class ClientInfo {
    private final int identifier;
    private final InetAddress ip;
    private final int port;
    private final Instant lastSeen;

    ClientInfo(int identifier, InetAddress ip, int port, Instant lastSeen) {
        this.identifier = identifier;
        this.ip = ip;
        this.port = port;
        this.lastSeen = lastSeen;
    }

    public int getIdentifier() {
        return identifier;
    }

    public InetAddress getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }
}
