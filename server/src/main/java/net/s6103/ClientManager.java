package net.s6103;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side client manager for UDP connections
 */
public class ClientManager {
//    private int heartBeat = 30;
    private final Map<Integer, ClientInfo> clients = new ConcurrentHashMap<>();

    public ClientManager() {}

//    public void setHeartbeat(int heartBeat) {
//        this.heartBeat = heartBeat;
//    }
//    public int getHeartBeat() {
//        return heartBeat;
//    }

    public void updateClient(int identifier, InetAddress ip, int port) {
        clients.put(identifier, new ClientInfo(identifier, ip, port, Instant.now()));
    }

    public void updateClient(int identifier, ClientInfo clientInfo) {
        clients.put(identifier, clientInfo);
    }

//    public void checkTimeouts() {
//        Instant now = Instant.now();
//        clients.values().removeIf(client ->
//                now.getEpochSecond() - client.getLastSeen().getEpochSecond() > heartBeat
//        );
//    }

    public ClientInfo findClient(int identifier) {
        return  clients.get(identifier);
    }
}
