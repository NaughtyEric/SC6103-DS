package net.s6103;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-side client manager for UDP connections
 */
public class ClientManager {
    private final List<ClientInfo> clients = new ArrayList<>();
    public ClientManager() {}

    public void addClient(ClientInfo info) {
        if (findClient(info) == null) clients.add(info);
    }
    public ClientInfo findClient(ClientInfo info) {
        for (ClientInfo c : clients) {
            if (c.getIp().equals(info.getIp()) && c.getPort() == info.getPort()) {
                return c;
            }
        }
        return null;
    }
}
