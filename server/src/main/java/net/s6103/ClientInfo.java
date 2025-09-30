package net.s6103;

import java.net.InetAddress;

public class ClientInfo {
    private final int identifier;
    private final InetAddress ip;
    private final int port;

    ClientInfo(int identifier, InetAddress ip, int port) {
        this.identifier = identifier;
        this.ip = ip;
        this.port = port;
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

}
