package net.s6103;

import java.net.InetAddress;

public class ClientInfo {
    private final InetAddress ip;
    private final int port;

    public ClientInfo(InetAddress ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public InetAddress getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

}
