package net.s6103;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 标准化的消息序列化和反序列化工具类
 * 支持网络字节序（大端序）和UTF-8字符串编码
 */
public class MessageSerializer {
    
    // 协议常量
    public static final int MAGIC = 0x46424B31; // 'FBK1'
    public static final int VERSION = 1;
    public static final int HEADER_SIZE = 24;
    
    // 操作码枚举
    public enum OpCode {
        QUERY_AVAILABILITY(1),
        BOOK(2),
        CHANGE(3),
        MONITOR(4),
        CANCEL(5),
        CHECK_IN(6);
        
        private final int value;
        
        OpCode(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
        
        public static OpCode fromValue(int value) {
            for (OpCode op : values()) {
                if (op.value == value) {
                    return op;
                }
            }
            throw new IllegalArgumentException("Unknown op code: " + value);
        }
    }
    
    // 调用语义枚举
    public enum Semantics {
        AT_LEAST_ONCE(0),
        AT_MOST_ONCE(1);
        
        private final int value;
        
        Semantics(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
        
        public static Semantics fromValue(int value) {
            for (Semantics s : values()) {
                if (s.value == value) {
                    return s;
                }
            }
            throw new IllegalArgumentException("Unknown semantics: " + value);
        }
    }
    
    // 消息头结构
    public static class MessageHeader {
        public int magic;
        public int version;
        public int requestId;
        public int opCode;
        public int timestamp;
        public int semantics;
        public int payloadLen;
        
        public MessageHeader() {}
        
        public MessageHeader(int requestId, OpCode opCode, Semantics semantics, int payloadLen) {
            this.magic = MAGIC;
            this.version = VERSION;
            this.requestId = requestId;
            this.opCode = opCode.getValue();
            this.timestamp = (int) (System.currentTimeMillis() / 1000);
            this.semantics = semantics.getValue();
            this.payloadLen = payloadLen;
        }
    }
    
    // 请求消息结构
    public static class RequestMessage {
        public MessageHeader header;
        public byte[] payload;
        
        public RequestMessage(MessageHeader header, byte[] payload) {
            this.header = header;
            this.payload = payload;
        }
    }
    
    // 响应消息结构
    public static class ResponseMessage {
        public MessageHeader header;
        public int status;
        public String message;
        public byte[] additionalData;
        
        public ResponseMessage(MessageHeader header, int status, String message, byte[] additionalData) {
            this.header = header;
            this.status = status;
            this.message = message;
            this.additionalData = additionalData;
        }
    }
    
    /**
     * 序列化消息头
     */
    public static byte[] serializeHeader(MessageHeader header) {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        buffer.putInt(header.magic);
        buffer.putInt(header.version);
        buffer.putInt(header.requestId);
        buffer.putInt(header.opCode);
        buffer.putInt(header.timestamp);
        buffer.putInt(header.semantics);
        buffer.putInt(header.payloadLen);
        
        return buffer.array();
    }
    
    /**
     * 反序列化消息头
     */
    public static MessageHeader deserializeHeader(byte[] data) throws SerializationException {
        if (data.length < HEADER_SIZE) {
            throw new SerializationException("Insufficient data for header");
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, HEADER_SIZE);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        MessageHeader header = new MessageHeader();
        header.magic = buffer.getInt();
        header.version = buffer.getInt();
        header.requestId = buffer.getInt();
        header.opCode = buffer.getInt();
        header.timestamp = buffer.getInt();
        header.semantics = buffer.getInt();
        header.payloadLen = buffer.getInt();
        
        // 验证魔数和版本
        if (header.magic != MAGIC) {
            throw new SerializationException("Invalid magic number: " + header.magic);
        }
        if (header.version != VERSION) {
            throw new SerializationException("Unsupported version: " + header.version);
        }
        
        return header;
    }
    
    /**
     * 序列化请求消息
     */
    public static byte[] serializeRequest(RequestMessage request) {
        byte[] headerBytes = serializeHeader(request.header);
        byte[] result = new byte[headerBytes.length + request.payload.length];
        
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
        System.arraycopy(request.payload, 0, result, headerBytes.length, request.payload.length);
        
        return result;
    }
    
    /**
     * 反序列化请求消息
     */
    public static RequestMessage deserializeRequest(byte[] data) throws SerializationException {
        MessageHeader header = deserializeHeader(data);
        
        if (data.length < HEADER_SIZE + header.payloadLen) {
            throw new SerializationException("Insufficient data for payload");
        }
        
        byte[] payload = new byte[header.payloadLen];
        System.arraycopy(data, HEADER_SIZE, payload, 0, header.payloadLen);
        
        return new RequestMessage(header, payload);
    }
    
    /**
     * 序列化响应消息
     */
    public static byte[] serializeResponse(ResponseMessage response) {
        byte[] messageBytes = response.message.getBytes(StandardCharsets.UTF_8);
        int additionalDataLen = response.additionalData != null ? response.additionalData.length : 0;
        int payloadLen = 4 + 4 + messageBytes.length + additionalDataLen;
        
        response.header.payloadLen = payloadLen;
        byte[] headerBytes = serializeHeader(response.header);
        
        ByteBuffer buffer = ByteBuffer.allocate(headerBytes.length + payloadLen);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        // 写入头部
        buffer.put(headerBytes);
        
        // 写入状态码
        buffer.putInt(response.status);
        
        // 写入消息长度和内容
        buffer.putInt(messageBytes.length);
        buffer.put(messageBytes);
        
        // 写入额外数据
        if (response.additionalData != null) {
            buffer.put(response.additionalData);
        }
        
        return buffer.array();
    }
    
    /**
     * 反序列化响应消息
     */
    public static ResponseMessage deserializeResponse(byte[] data) throws SerializationException {
        MessageHeader header = deserializeHeader(data);
        
        if (data.length < HEADER_SIZE + 8) { // 至少需要状态码和消息长度
            throw new SerializationException("Insufficient data for response");
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(data, HEADER_SIZE, data.length - HEADER_SIZE);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        int status = buffer.getInt();
        int messageLen = buffer.getInt();
        
        if (buffer.remaining() < messageLen) {
            throw new SerializationException("Insufficient data for message");
        }
        
        byte[] messageBytes = new byte[messageLen];
        buffer.get(messageBytes);
        String message = new String(messageBytes, StandardCharsets.UTF_8);
        
        byte[] additionalData = null;
        if (buffer.hasRemaining()) {
            additionalData = new byte[buffer.remaining()];
            buffer.get(additionalData);
        }
        
        return new ResponseMessage(header, status, message, additionalData);
    }
    
    // 辅助方法：序列化字符串
    public static void serializeString(ByteBuffer buffer, String str) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(bytes.length);
        buffer.put(bytes);
    }
    
    // 辅助方法：反序列化字符串
    public static String deserializeString(ByteBuffer buffer) throws SerializationException {
        if (buffer.remaining() < 4) {
            throw new SerializationException("Insufficient data for string length");
        }
        
        int length = buffer.getInt();
        if (length < 0 || buffer.remaining() < length) {
            throw new SerializationException("Invalid string length: " + length);
        }
        
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    
    // 辅助方法：序列化整数列表
    public static void serializeIntList(ByteBuffer buffer, List<Integer> list) {
        buffer.putInt(list.size());
        for (Integer value : list) {
            buffer.putInt(value);
        }
    }
    
    // 辅助方法：反序列化整数列表
    public static List<Integer> deserializeIntList(ByteBuffer buffer) throws SerializationException {
        if (buffer.remaining() < 4) {
            throw new SerializationException("Insufficient data for list size");
        }
        
        int size = buffer.getInt();
        if (size < 0 || size > 100) { // 防止异常大的列表
            throw new SerializationException("Invalid list size: " + size);
        }
        
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (buffer.remaining() < 4) {
                throw new SerializationException("Insufficient data for list element " + i);
            }
            list.add(buffer.getInt());
        }
        
        return list;
    }
    
    /**
     * 序列化异常
     */
    public static class SerializationException extends Exception {
        public SerializationException(String message) {
            super(message);
        }
        
        public SerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
