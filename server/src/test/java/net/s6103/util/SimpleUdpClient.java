package net.s6103.util;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class SimpleUdpClient implements Runnable {
    private final int port;
    private volatile boolean running = true;

    public SimpleUdpClient(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            byte[] buf = new byte[1024];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();
                DatagramPacket response = new DatagramPacket(
                        packet.getData(), packet.getLength(),
                        clientAddress, clientPort
                );
                socket.send(response);
            }
        } catch (IOException e) {
            if (running) {
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        running = false;
        // 通过发送一个空包唤醒阻塞的 receive
        try (DatagramSocket s = new DatagramSocket()) {
            s.send(new DatagramPacket(new byte[1], 1, InetAddress.getLoopbackAddress(), port));
        } catch (IOException ignored) {}
    }
}
