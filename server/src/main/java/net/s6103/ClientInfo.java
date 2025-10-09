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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ClientInfo that = (ClientInfo) obj;
        return port == that.port && ip.equals(that.ip);
    }

    @Override
    public int hashCode() {
        return ip.hashCode() * 31 + port;
    }

    @Override
    public String toString() {
        return ip.getHostAddress() + ":" + port;
    }
}
