package net.s6103;

import java.util.ArrayList;
import java.util.Iterator;
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
            if (c.ip().equals(info.ip()) && c.port() == info.port()) {
                return c;
            }
        }
        return null;
    }

    public ClientInfo removeClient(ClientInfo info) {
        Iterator<ClientInfo> it = clients.iterator();
        while (it.hasNext()) {
            ClientInfo c = it.next();
            if (c.ip().equals(info.ip()) && c.port() == info.port()) {
                it.remove();
                return c;
            }
        }
        return null;

    }
}
