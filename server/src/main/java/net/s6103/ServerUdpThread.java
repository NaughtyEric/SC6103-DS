package net.s6103;

import java.net.*;

public class ServerUdpThread extends Thread {
    static final int BUFFER_SIZE = 4096;
    private final int port;
    private final ClientManager manager;

    public ServerUdpThread(int port) {
        this.port = port;
        this.manager = new ClientManager();
    }

    public ServerUdpThread(int port, ClientManager manager) {
        this.port = port;
        this.manager = manager;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            System.out.println("UDP Server started on port " + port);

            while (true) {
                /* TODO: 添加功能 */

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
