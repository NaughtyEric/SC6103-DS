package net.s6103;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 请求去重和重试管理器
 * 支持At-Least-Once和At-Most-Once语义
 */
public class RequestManager {
    
    private static final Logger logger = Logger.getLogger(RequestManager.class.getName());
    
    // 请求缓存，用于去重
    private final ConcurrentHashMap<String, CachedRequest> requestCache = new ConcurrentHashMap<>();
    
    // 清理过期请求的调度器
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
    
    // 缓存过期时间（秒）
    private static final int CACHE_EXPIRY_SECONDS = 300; // 5分钟
    
    public RequestManager() {
        // 每30秒清理一次过期请求
        cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredRequests, 30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * 处理请求，根据语义决定是否去重
     */
    public ProcessResult processRequest(MessageSerializer.RequestMessage request, 
                                     RequestProcessor processor) {
        String requestKey = generateRequestKey(request);
        MessageSerializer.Semantics semantics = MessageSerializer.Semantics.fromValue(request.header.semantics);
        
        // 检查是否已处理过此请求（At-Most-Once语义）
        if (semantics == MessageSerializer.Semantics.AT_MOST_ONCE) {
            CachedRequest cached = requestCache.get(requestKey);
            if (cached != null && !cached.isExpired()) {
                logger.info("Returning cached response for request " + request.header.requestId);
                return new ProcessResult(true, cached.response, true);
            }
        }
        
        try {
            // 处理请求
            MessageSerializer.ResponseMessage response = processor.process(request);
            
            // 缓存响应（At-Most-Once语义）
            if (semantics == MessageSerializer.Semantics.AT_MOST_ONCE) {
                cacheRequest(requestKey, response);
            }
            
            return new ProcessResult(true, response, false);
            
        } catch (Exception e) {
            logger.severe("Error processing request " + request.header.requestId + ": " + e.getMessage());
            MessageSerializer.ResponseMessage errorResponse = createErrorResponse(
                request.header.requestId, 4, "Server error: " + e.getMessage());
            return new ProcessResult(false, errorResponse, false);
        }
    }
    
    /**
     * 生成请求的唯一键
     */
    private String generateRequestKey(MessageSerializer.RequestMessage request) {
        return request.header.requestId + "_" + request.header.opCode + "_" + 
               request.header.timestamp + "_" + request.header.semantics;
    }
    
    /**
     * 缓存请求响应
     */
    private void cacheRequest(String requestKey, MessageSerializer.ResponseMessage response) {
        CachedRequest cached = new CachedRequest(response, Instant.now());
        requestCache.put(requestKey, cached);
    }
    
    /**
     * 清理过期请求
     */
    private void cleanupExpiredRequests() {
        Instant now = Instant.now();
        requestCache.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired(now);
            if (expired) {
                logger.fine("Removing expired request: " + entry.getKey());
            }
            return expired;
        });
    }
    
    /**
     * 创建错误响应
     */
    private MessageSerializer.ResponseMessage createErrorResponse(int requestId, int status, String message) {
        MessageSerializer.MessageHeader header = new MessageSerializer.MessageHeader(
            requestId, null, MessageSerializer.Semantics.AT_LEAST_ONCE, 0);
        return new MessageSerializer.ResponseMessage(header, status, message, null);
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
     * 缓存的请求
     */
    private static class CachedRequest {
        private final MessageSerializer.ResponseMessage response;
        private final Instant timestamp;
        
        public CachedRequest(MessageSerializer.ResponseMessage response, Instant timestamp) {
            this.response = response;
            this.timestamp = timestamp;
        }
        
        public boolean isExpired() {
            return isExpired(Instant.now());
        }
        
        public boolean isExpired(Instant now) {
            return now.isAfter(timestamp.plusSeconds(CACHE_EXPIRY_SECONDS));
        }
    }
    
    /**
     * 处理结果
     */
    public static class ProcessResult {
        public final boolean success;
        public final MessageSerializer.ResponseMessage response;
        public final boolean fromCache;
        
        public ProcessResult(boolean success, MessageSerializer.ResponseMessage response, boolean fromCache) {
            this.success = success;
            this.response = response;
            this.fromCache = fromCache;
        }
    }
    
    /**
     * 请求处理器接口
     */
    @FunctionalInterface
    public interface RequestProcessor {
        MessageSerializer.ResponseMessage process(MessageSerializer.RequestMessage request) throws Exception;
    }
}
