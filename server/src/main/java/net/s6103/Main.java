package net.s6103;

public class Main {
    public static void main(String[] args) {
        int port = 9000; // 默认端口
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default 9000");
            }
        }
        
        System.out.println("Starting UDP Server on port " + port);
        ServerUdpThread server = new ServerUdpThread(port);
        server.start();
        
        try {
            server.join();
        } catch (InterruptedException e) {
            System.err.println("Server interrupted");
        }
    }
}