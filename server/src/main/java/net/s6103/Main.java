package net.s6103;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 主程序入口
 * 启动重构后的UDP服务器
 */
public class Main {
    
    private static final Logger logger = Logger.getLogger(Main.class.getName());
    
    public static void main(String[] args) {
        // 配置日志级别
        Logger.getLogger("net.s6103").setLevel(Level.INFO);
        
        int port = 9000; // 默认端口
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
                if (port < 1 || port > 65535) {
                    throw new IllegalArgumentException("Port must be between 1 and 65535");
                }
            } catch (NumberFormatException e) {
                logger.severe("Invalid port number: " + args[0] + ", using default 9000");
                port = 9000;
            }
        }
        
        logger.info("Starting UDP Server on port " + port);
        
        ServerUdpThread server = new ServerUdpThread(port);
        server.start();
        
        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down server...");
            server.stopServer();
            try {
                server.join(5000); // 等待最多5秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        
        try {
            server.join();
        } catch (InterruptedException e) {
            logger.info("Server interrupted");
            Thread.currentThread().interrupt();
        }
        
        logger.info("Server stopped");
    }
}