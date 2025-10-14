package net.s6103;

import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 重构的UDP服务器线程
 * 使用标准化的消息序列化和业务逻辑分离
 */
public class ServerUdpThread extends Thread {
    
    private static final Logger logger = Logger.getLogger(ServerUdpThread.class.getName());
    private static final int BUFFER_SIZE = 4096;
    private static final int THREAD_MAXIMUM = 10;
    
    private final int port;
    private final ClientManager clientManager;
    private final AppointmentManager appointmentManager;
    // Cache for At-Most-Once semantics: (client, requestId) -> response bytes
    private final Map<RequestKey, CachedResponse> responseCache = new ConcurrentHashMap<>();
    // Cache retention (milliseconds)
    private static final long CACHE_TTL_MS = 60_000; // keep for 60 seconds
    private final BusinessLogicHandler businessHandler;
    private final RequestManager requestManager;
    private final ExecutorService executor;
    
    private volatile boolean running = true;

    public ServerUdpThread(int port) {
        this.port = port;
        this.clientManager = new ClientManager();
        this.appointmentManager = new AppointmentManager();
        this.businessHandler = new BusinessLogicHandler(appointmentManager, clientManager);
        this.requestManager = new RequestManager();
        this.executor = Executors.newFixedThreadPool(THREAD_MAXIMUM);
    }

    public ServerUdpThread(int port, ClientManager clientManager) {
        this.port = port;
        this.clientManager = clientManager;
        this.appointmentManager = new AppointmentManager();
        this.businessHandler = new BusinessLogicHandler(appointmentManager, clientManager);
        this.requestManager = new RequestManager();
        this.executor = Executors.newFixedThreadPool(THREAD_MAXIMUM);
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            logger.info("UDP Server started on port " + port);
            
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);
                    socket.receive(packet);

                    // 异步处理请求
                    executor.submit(() -> processRequestAsync(socket, packet));
                    
                } catch (Exception e) {
                    if (running) {
                        logger.severe("Error receiving packet: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("Failed to start UDP server: " + e.getMessage());
        } finally {
            shutdown();
        }
    }
    
    /**
     * 异步处理请求
     */
    private void processRequestAsync(DatagramSocket socket, DatagramPacket packet) {
        try {
            System.out.println("Received " + data.length + " bytes from " + clientAddress + ":" + clientPort);
            if (data.length < 24) { // Minimum message header length
                System.err.println("Message too short: " + data.length + " bytes");
                return createErrorResponse(4, "Invalid message format");
            }

            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.order(ByteOrder.BIG_ENDIAN);

            // Parse message header
            int magic = buffer.getInt();
            int version = buffer.getInt();
            int requestId = buffer.getInt();
            int opCode = buffer.getInt();
            int timestamp = buffer.getInt();
            int semantics = buffer.getInt();
            int payloadLen = buffer.getInt();

            System.out.println("Parsed header: magic=0x" + Integer.toHexString(magic) + 
                             ", version=" + version + ", requestId=" + requestId + 
                             ", opCode=" + opCode + ", payloadLen=" + payloadLen);

            // Validate magic number and version
            if (magic != MAGIC) {
                System.err.println("Invalid magic: expected 0x" + Integer.toHexString(MAGIC) + 
                                 ", got 0x" + Integer.toHexString(magic));
                return createErrorResponse(4, "Invalid magic number");
            }
            if (version != VERSION) {
                System.err.println("Invalid version: expected " + VERSION + ", got " + version);
                return createErrorResponse(4, "Unsupported version");
            }

            // Create client info
            ClientInfo clientInfo = new ClientInfo(clientAddress, clientPort);
            manager.addClient(clientInfo);

            // At-Most-Once duplicate filtering using cache
            RequestKey key = new RequestKey(clientInfo, requestId);
            long now = System.currentTimeMillis();
            if (semantics == 1) { // AtMostOnce
                CachedResponse cached = responseCache.get(key);
                if (cached != null && (now - cached.timestampMs) <= CACHE_TTL_MS) {
                    System.out.println("Cache hit for requestId=" + requestId + " from " + clientInfo);
                    return cached.responseBytes;
                }
                // else miss/expired: process, then cache below
            }

            // Handle different types of requests
            byte[] response = switch (opCode) {
                case 1 -> // QueryAvailability
                        handleQueryAvailability(buffer, payloadLen, requestId);
                case 2 -> // Book
                        handleBook(buffer, payloadLen, requestId, clientInfo);
                case 3 -> // Change
                        handleChange(buffer, payloadLen, requestId, clientInfo);
                case 4 -> // Monitor
                        handleMonitor(buffer, payloadLen, requestId, clientInfo);
                case 5 -> // Cancel
                        handleCancel(buffer, payloadLen, requestId, clientInfo);
                case 6 -> // CheckIn
                        handleCheckIn(buffer, payloadLen, requestId, clientInfo);
                default -> createErrorResponse(3, "Unknown operation");
            };
            // Store in cache only for At-Most-Once semantics
            if (semantics == 1 && response != null) {
                responseCache.put(key, new CachedResponse(response, now));
                // Opportunistic cleanup of expired entries
                cleanupCache(now);
            }
            return response;
        } catch (Exception e) {
            logger.severe("Error processing request from " + packet.getAddress() + 
                         ":" + packet.getPort() + ": " + e.getMessage());
        }
    }

    private void cleanupCache(long nowMs) {
        // Simple linear cleanup; acceptable for assignment-scale loads
        for (Map.Entry<RequestKey, CachedResponse> entry : responseCache.entrySet()) {
            if (nowMs - entry.getValue().timestampMs > CACHE_TTL_MS) {
                responseCache.remove(entry.getKey());
            }
        }
    }

    private byte[] handleQueryAvailability(ByteBuffer buffer, int payloadLen, int requestId) {
        try {
            System.out.println("handleQueryAvailability: payloadLen=" + payloadLen);
            if (payloadLen < 4) {
                return createErrorResponse(3, "Invalid payload");
            }

            // 读取设施名称
            int facilityNameLen = buffer.getInt();
            System.out.println("facilityNameLen=" + facilityNameLen);
            if (facilityNameLen < 0 || facilityNameLen > payloadLen - 4) {
                return createErrorResponse(3, "Invalid facility name length");
            }

            byte[] facilityNameBytes = new byte[facilityNameLen];
            buffer.get(facilityNameBytes);
            String facilityName = new String(facilityNameBytes, StandardCharsets.UTF_8);
            System.out.println("facilityName='" + facilityName + "'");

            // 读取查询天数
            int dayCount = buffer.getInt();
            System.out.println("dayCount=" + dayCount);
            if (dayCount < 1 || dayCount > 7) {
                System.err.println("Invalid day count: " + dayCount + " (expected 1-7)");
                return createErrorResponse(3, "Invalid day count");
            }

            // 读取天数列表
            List<Integer> days = new ArrayList<>();
            for (int i = 0; i < dayCount; i++) {
                days.add(buffer.getInt());
            }

            // Query availability
            var handle = appointmentManager.getHandle(new ClientInfo(null, 0));
            List<String> results = new ArrayList<>();

            for (int day : days) {
                LocalDate date = LocalDate.now().plusDays(day - 1);
                Appointment[] appointments = handle.query(facilityName, date);

                StringBuilder dayResult = new StringBuilder();
                dayResult.append("Day ").append(day).append(": ");
                if (appointments.length == 0) {
                    dayResult.append("Available all day");
                } else {
                    dayResult.append("Booked: ");
                    for (Appointment apt : appointments) {
                        dayResult.append(apt.getBeginTime().toString()).append("-")
                               .append(apt.getEndTime().toString()).append(" ");
                    }
                }
                results.add(dayResult.toString());
            }

            return createSuccessResponse(requestId, String.join("; ", results));

        } catch (Exception e) {
            logger.severe("Error processing request: " + e.getMessage());
            return createErrorResponse(0, 4, "Server error: " + e.getMessage());
        }
    }
    
    /**
     * 处理业务请求
     */
    private MessageSerializer.ResponseMessage handleBusinessRequest(MessageSerializer.RequestMessage request, 
                                                                   ClientInfo clientInfo) throws Exception {
        
        MessageSerializer.OpCode opCode = MessageSerializer.OpCode.fromValue(request.header.opCode);
        
        return switch (opCode) {
            case QUERY_AVAILABILITY -> businessHandler.handleQueryAvailability(request);
            case BOOK -> businessHandler.handleBook(request, clientInfo);
            case CHANGE -> businessHandler.handleChange(request, clientInfo);
            case MONITOR -> businessHandler.handleMonitor(request, clientInfo);
            case CANCEL -> businessHandler.handleCancel(request, clientInfo);
            case CHECK_IN -> businessHandler.handleCheckIn(request, clientInfo);
            default -> {
                logger.warning("Unknown operation: " + request.header.opCode);
                MessageSerializer.MessageHeader header = new MessageSerializer.MessageHeader(
                    request.header.requestId, null, MessageSerializer.Semantics.AT_LEAST_ONCE, 0);
                yield new MessageSerializer.ResponseMessage(header, 3, "Unknown operation", null);
            }
        };
    }

    /**
     * 创建错误响应
     */
    private byte[] createErrorResponse(int requestId, int status, String message) {
        MessageSerializer.MessageHeader header = new MessageSerializer.MessageHeader(
            requestId, null, MessageSerializer.Semantics.AT_LEAST_ONCE, 0);
        MessageSerializer.ResponseMessage response = new MessageSerializer.ResponseMessage(
            header, status, message, null);
        return MessageSerializer.serializeResponse(response);
    }
    
    /**
     * 停止服务器
     */
    public void stopServer() {
        running = false;
        interrupt();
    }
    
    /**
     * 关闭服务器
     */
    public void shutdown() {
        running = false;
        
        // 关闭线程池
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 关闭管理器
        clientManager.shutdown();
        requestManager.shutdown();
        
        logger.info("Server shutdown completed");
    }

    private byte[] handleCheckIn(ByteBuffer buffer, int payloadLen, int requestId, ClientInfo clientInfo) {
        try {
            int appointmentId = buffer.getInt();

            var handle = appointmentManager.getHandle(clientInfo);
            int result = handle.checkIn(appointmentId);

            if (result == 0) {
                return createSuccessResponse(requestId, "Check-in successful");
            } else if (result == 1) {
                return createErrorResponse(2, "Check-in failed - already checked in");
            } else if (result == 2) {
                return createErrorResponse(2, "Check-in failed - timeout");
            } else {
                return createErrorResponse(2, "Check-in failed - not found or not authorized");
            }

        } catch (Exception e) {
            return createErrorResponse(4, "Check-in error: " + e.getMessage());
        }
    }

    private byte[] createSuccessResponse(int requestId, String message) {
        return createResponse(requestId, 0, message);
    }

    private byte[] createErrorResponse(int status, String message) {
        return createResponse(0, status, message);
    }

    private byte[] createResponse(int requestId, int status, String message) {
        try {
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            int headerLen = 7 * 4; // 7 int fields * 4 bytes each = 28 bytes
            int payloadLen = 4 + 4 + messageBytes.length; // status + messageLen + message
            int totalLen = headerLen + payloadLen;

            ByteBuffer buffer = ByteBuffer.allocate(totalLen);
            buffer.order(ByteOrder.BIG_ENDIAN);

            // Message header (7 int fields = 28 bytes)
            buffer.putInt(MAGIC);
            buffer.putInt(VERSION);
            buffer.putInt(requestId);
            buffer.putInt(0); // opCode (response)
            buffer.putInt((int)(System.currentTimeMillis() / 1000)); // timestamp
            buffer.putInt(0); // semantics
            buffer.putInt(payloadLen); // payloadLen

            // Status and message
            buffer.putInt(status);
            buffer.putInt(messageBytes.length);
            buffer.put(messageBytes);

            System.out.println("Created response: requestId=" + requestId + ", status=" + status + 
                             ", message='" + message + "', totalLen=" + totalLen);
            return buffer.array();
        } catch (Exception e) {
            System.err.println("Error creating response: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    
    /**
     * 获取服务器状态
     */
    public String getStatus() {
        return String.format("Server running on port %d, active clients: %d", 
                           port, clientManager.getActiveClientCount());
    }

    // RequestKey identifies a unique request from a client
    private static final class RequestKey {
        private final ClientInfo client;
        private final int requestId;
        private final int hash;

        RequestKey(ClientInfo client, int requestId) {
            this.client = client;
            this.requestId = requestId;
            this.hash = client.hashCode() * 31 + requestId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RequestKey that = (RequestKey) o;
            return requestId == that.requestId && client.equals(that.client);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final class CachedResponse {
        final byte[] responseBytes;
        final long timestampMs;
        CachedResponse(byte[] responseBytes, long timestampMs) {
            this.responseBytes = responseBytes;
            this.timestampMs = timestampMs;
        }
    }
}
