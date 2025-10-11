package net.s6103;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务器集成测试
 * 测试重构后的网络通信和业务逻辑
 */
class ServerIntegrationTest {
    
    private ServerUdpThread server;
    private ClientManager clientManager;
    private AppointmentManager appointmentManager;
    private BusinessLogicHandler businessHandler;
    
    @BeforeEach
    void setUp() {
        clientManager = new ClientManager();
        appointmentManager = new AppointmentManager();
        businessHandler = new BusinessLogicHandler(appointmentManager, clientManager);
        server = new ServerUdpThread(9001, clientManager); // 使用不同端口避免冲突
    }
    
    @AfterEach
    void tearDown() {
        if (server != null) {
            server.shutdown();
        }
    }
    
    @Test
    void testMessageSerialization() throws Exception {
        // 测试消息序列化和反序列化
        MessageSerializer.MessageHeader header = new MessageSerializer.MessageHeader(
            123, MessageSerializer.OpCode.QUERY_AVAILABILITY, 
            MessageSerializer.Semantics.AT_LEAST_ONCE, 10);
        
        byte[] headerBytes = MessageSerializer.serializeHeader(header);
        MessageSerializer.MessageHeader deserialized = MessageSerializer.deserializeHeader(headerBytes);
        
        assertEquals(header.magic, deserialized.magic);
        assertEquals(header.version, deserialized.version);
        assertEquals(header.requestId, deserialized.requestId);
        assertEquals(header.opCode, deserialized.opCode);
        assertEquals(header.semantics, deserialized.semantics);
        assertEquals(header.payloadLen, deserialized.payloadLen);
    }
    
    @Test
    void testStringSerialization() throws Exception {
        // 测试字符串序列化
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        String testString = "Test Facility Name";
        MessageSerializer.serializeString(buffer, testString);
        
        buffer.flip();
        String deserialized = MessageSerializer.deserializeString(buffer);
        
        assertEquals(testString, deserialized);
    }
    
    @Test
    void testIntListSerialization() throws Exception {
        // 测试整数列表序列化
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        List<Integer> testList = List.of(1, 2, 3, 4, 5);
        MessageSerializer.serializeIntList(buffer, testList);
        
        buffer.flip();
        List<Integer> deserialized = MessageSerializer.deserializeIntList(buffer);
        
        assertEquals(testList, deserialized);
    }
    
    @Test
    void testClientManager() {
        // 测试客户端管理器
        try {
            InetAddress address = InetAddress.getByName("127.0.0.1");
            int port = 12345;
            
            // 添加客户端
            ClientInfo client1 = clientManager.addOrUpdateClient(address, port);
            assertNotNull(client1);
            assertEquals(1, clientManager.getActiveClientCount());
            
            // 再次添加相同客户端应该更新会话
            ClientInfo client2 = clientManager.addOrUpdateClient(address, port);
            assertEquals(client1, client2);
            assertEquals(1, clientManager.getActiveClientCount());
            
            // 查找客户端
            ClientInfo found = clientManager.findClient(address, port);
            assertEquals(client1, found);
            
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }
    
    @Test
    void testBusinessLogicHandler() throws Exception {
        // 测试业务逻辑处理器
        ClientInfo clientInfo = new ClientInfo(InetAddress.getByName("127.0.0.1"), 12345);
        
        // 创建查询请求
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.order(ByteOrder.BIG_ENDIAN);
        MessageSerializer.serializeString(buffer, "Room101");
        buffer.putInt(2); // 查询2天
        buffer.putInt(1); // 星期一
        buffer.putInt(2); // 星期二
        
        byte[] payload = new byte[buffer.position()];
        buffer.flip();
        buffer.get(payload);
        
        MessageSerializer.MessageHeader header = new MessageSerializer.MessageHeader(
            1, MessageSerializer.OpCode.QUERY_AVAILABILITY, 
            MessageSerializer.Semantics.AT_LEAST_ONCE, payload.length);
        
        MessageSerializer.RequestMessage request = new MessageSerializer.RequestMessage(header, payload);
        
        // 处理请求
        MessageSerializer.ResponseMessage response = businessHandler.handleQueryAvailability(request);
        
        assertNotNull(response);
        assertEquals(0, response.status); // 成功状态
        assertNotNull(response.message);
    }
    
    @Test
    void testRequestManager() throws Exception {
        // 测试请求管理器
        RequestManager requestManager = new RequestManager();
        
        // 创建测试请求
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.order(ByteOrder.BIG_ENDIAN);
        MessageSerializer.serializeString(buffer, "Room101");
        buffer.putInt(1);
        buffer.putInt(1);
        
        byte[] payload = new byte[buffer.position()];
        buffer.flip();
        buffer.get(payload);
        
        MessageSerializer.MessageHeader header = new MessageSerializer.MessageHeader(
            1, MessageSerializer.OpCode.QUERY_AVAILABILITY, 
            MessageSerializer.Semantics.AT_LEAST_ONCE, payload.length);
        
        MessageSerializer.RequestMessage request = new MessageSerializer.RequestMessage(header, payload);
        
        // 处理请求
        RequestManager.ProcessResult result = requestManager.processRequest(request, req -> {
            MessageSerializer.MessageHeader respHeader = new MessageSerializer.MessageHeader(
                req.header.requestId, null, MessageSerializer.Semantics.AT_LEAST_ONCE, 0);
            return new MessageSerializer.ResponseMessage(respHeader, 0, "Test response", null);
        });
        
        assertTrue(result.success);
        assertNotNull(result.response);
        assertEquals(0, result.response.status);
        assertEquals("Test response", result.response.message);
        
        requestManager.shutdown();
    }
}
