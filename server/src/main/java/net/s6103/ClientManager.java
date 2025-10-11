package net.s6103;

import java.net.InetAddress;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 改进的客户端管理器
 * 支持会话管理、连接状态跟踪和自动清理
 */
public class ClientManager {
    
    private static final Logger logger = Logger.getLogger(ClientManager.class.getName());
    
    // 客户端会话映射
    private final ConcurrentHashMap<String, ClientSession> clientSessions = new ConcurrentHashMap<>();
    
    // 清理过期会话的调度器
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
    
    // 会话超时时间（秒）
    private static final int SESSION_TIMEOUT_SECONDS = 1800; // 30分钟
    
    public ClientManager() {
        // 每5分钟清理一次过期会话
        cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredSessions, 300, 300, TimeUnit.SECONDS);
    }
    
    /**
     * 添加或更新客户端会话
     */
    public ClientInfo addOrUpdateClient(InetAddress address, int port) {
        String clientKey = generateClientKey(address, port);
        
        ClientSession session = clientSessions.computeIfAbsent(clientKey, _ -> {
            ClientInfo info = new ClientInfo(address, port);
            logger.info("New client connected: " + info);
            return new ClientSession(info);
        });
        
        // 更新最后活动时间
        session.updateLastActivity();
        return session.getClientInfo();
    }
    
    /**
     * 查找客户端
     */
    public ClientInfo findClient(InetAddress address, int port) {
        String clientKey = generateClientKey(address, port);
        ClientSession session = clientSessions.get(clientKey);
        return session != null ? session.getClientInfo() : null;
    }
    
    /**
     * 查找客户端（兼容旧接口）
     */
    public ClientInfo findClient(ClientInfo info) {
        return findClient(info.getIp(), info.getPort());
    }
    
    /**
     * 获取客户端会话
     */
    public ClientSession getClientSession(InetAddress address, int port) {
        String clientKey = generateClientKey(address, port);
        return clientSessions.get(clientKey);
    }
    
    /**
     * 移除客户端
     */
    public void removeClient(InetAddress address, int port) {
        String clientKey = generateClientKey(address, port);
        ClientSession session = clientSessions.remove(clientKey);
        if (session != null) {
            logger.info("Client disconnected: " + session.getClientInfo());
        }
    }
    
    /**
     * 获取活跃客户端数量
     */
    public int getActiveClientCount() {
        return clientSessions.size();
    }
    
    /**
     * 生成客户端唯一键
     */
    private String generateClientKey(InetAddress address, int port) {
        return address.getHostAddress() + ":" + port;
    }
    
    /**
     * 清理过期会话
     */
    private void cleanupExpiredSessions() {
        Instant now = Instant.now();
        clientSessions.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired(now);
            if (expired) {
                logger.info("Removing expired session: " + entry.getKey());
            }
            return expired;
        });
    }
    
    /**
     * 关闭管理器
     */
    public void shutdown() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 客户端会话
     */
    public static class ClientSession {
        private final ClientInfo clientInfo;
        private Instant lastActivity;
        private int requestCount;
        private Instant firstConnected;
        
        public ClientSession(ClientInfo clientInfo) {
            this.clientInfo = clientInfo;
            this.lastActivity = Instant.now();
            this.requestCount = 0;
            this.firstConnected = Instant.now();
        }
        
        public ClientInfo getClientInfo() {
            return clientInfo;
        }
        
        public void updateLastActivity() {
            this.lastActivity = Instant.now();
            this.requestCount++;
        }
        
        public Instant getLastActivity() {
            return lastActivity;
        }
        
        public int getRequestCount() {
            return requestCount;
        }
        
        public Instant getFirstConnected() {
            return firstConnected;
        }
        
        public boolean isExpired() {
            return isExpired(Instant.now());
        }
        
        public boolean isExpired(Instant now) {
            return now.isAfter(lastActivity.plusSeconds(SESSION_TIMEOUT_SECONDS));
        }
        
        @Override
        public String toString() {
            return "ClientSession{" +
                    "clientInfo=" + clientInfo +
                    ", lastActivity=" + lastActivity +
                    ", requestCount=" + requestCount +
                    ", firstConnected=" + firstConnected +
                    '}';
        }
    }
}
