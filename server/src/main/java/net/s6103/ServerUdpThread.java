package net.s6103;

import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
            // 复制数据
            byte[] data = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());
            
            // 处理请求
            byte[] response = processRequest(data, packet.getAddress(), packet.getPort());
            
            if (response != null) {
                // 发送响应
                DatagramPacket responsePacket = new DatagramPacket(
                    response, response.length,
                    packet.getAddress(), packet.getPort()
                );
                socket.send(responsePacket);
            }
            
        } catch (Exception e) {
            logger.severe("Error processing request from " + packet.getAddress() + 
                         ":" + packet.getPort() + ": " + e.getMessage());
        }
    }

    private byte[] processRequest(byte[] data, InetAddress clientAddress, int clientPort) {
        try {
            // 解析请求消息
            MessageSerializer.RequestMessage request = MessageSerializer.deserializeRequest(data);
            
            // 添加或更新客户端会话
            ClientInfo clientInfo = clientManager.addOrUpdateClient(clientAddress, clientPort);
            
            // 使用请求管理器处理请求（支持去重和重试）
            RequestManager.ProcessResult result = requestManager.processRequest(request, req -> {
                return handleBusinessRequest(req, clientInfo);
            });
            
            // 序列化响应
            return MessageSerializer.serializeResponse(result.response);
            
        } catch (MessageSerializer.SerializationException e) {
            logger.warning("Serialization error: " + e.getMessage());
            return createErrorResponse(0, 4, "Invalid message format: " + e.getMessage());
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
    
    /**
     * 获取服务器状态
     */
    public String getStatus() {
        return String.format("Server running on port %d, active clients: %d", 
                           port, clientManager.getActiveClientCount());
    }
}
