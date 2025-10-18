package net.s6103;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 业务逻辑处理器
 * 处理具体的预约管理业务逻辑
 */
public class BusinessLogicHandler {
    
    private final AppointmentManager appointmentManager;
    
    public BusinessLogicHandler(AppointmentManager appointmentManager, ClientManager clientManager) {
        this.appointmentManager = appointmentManager;
    }
    
    /**
     * 处理查询可用性请求
     */
    public MessageSerializer.ResponseMessage handleQueryAvailability(MessageSerializer.RequestMessage request) 
            throws Exception {
        
        ByteBuffer buffer = ByteBuffer.wrap(request.payload);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        // 解析设施名称
        String facilityName = MessageSerializer.deserializeString(buffer);
        
        // 解析查询天数
        int dayCount = buffer.getInt();
        if (dayCount < 1 || dayCount > 7) {
            return createErrorResponse(request.header.requestId, 3, "Invalid day count: " + dayCount);
        }
        
        // 解析天数列表
        List<Integer> days = new ArrayList<>();
        for (int i = 0; i < dayCount; i++) {
            days.add(buffer.getInt());
        }
        
        // 验证设施名称
        if (!ValidFacilities.isValidFacility(facilityName)) {
            return createErrorResponse(request.header.requestId, 1, "Invalid facility: " + facilityName);
        }
        
        // 查询可用性
        var handle = appointmentManager.getHandle(new ClientInfo(null, 0));
        List<String> results = new ArrayList<>();
        
        for (int day : days) {
            if (day < 1 || day > 7) {
                return createErrorResponse(request.header.requestId, 3, "Invalid day: " + day);
            }
            
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
        
        String message = String.join("; ", results);
        return createSuccessResponse(request.header.requestId, message);
    }
    
    /**
     * 处理预订请求
     */
    public MessageSerializer.ResponseMessage handleBook(MessageSerializer.RequestMessage request, 
                                                       ClientInfo clientInfo) throws Exception {
        
        ByteBuffer buffer = ByteBuffer.wrap(request.payload);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        // 解析预订参数
        String facilityName = MessageSerializer.deserializeString(buffer);
        int startDay = buffer.getInt();
        int startHour = buffer.getInt();
        int startMin = buffer.getInt();
        int endDay = buffer.getInt();
        int endHour = buffer.getInt();
        int endMin = buffer.getInt();
        
        // 验证参数
        if (!ValidFacilities.isValidFacility(facilityName)) {
            return createErrorResponse(request.header.requestId, 1, "Invalid facility: " + facilityName);
        }
        
        if (!isValidTime(startDay, startHour, startMin) || !isValidTime(endDay, endHour, endMin)) {
            return createErrorResponse(request.header.requestId, 3, "Invalid time parameters");
        }
        
        // 计算时间
        LocalDate startDate = LocalDate.now().plusDays(startDay - 1);
        Instant startTime = startDate.atTime(startHour, startMin).toInstant(ZoneOffset.UTC);
        LocalDate endDate = LocalDate.now().plusDays(endDay - 1);
        Instant endTime = endDate.atTime(endHour, endMin).toInstant(ZoneOffset.UTC);
        
        if (startTime.isAfter(endTime)) {
            return createErrorResponse(request.header.requestId, 3, "Start time must be before end time");
        }
        
        // 执行预订
        var handle = appointmentManager.getHandle(clientInfo);
        int appointmentId = handle.book(facilityName, startTime, endTime);
        
        if (appointmentId > 0) {
            return createSuccessResponse(request.header.requestId, "Booking successful, ID: " + appointmentId);
        } else {
            return createErrorResponse(request.header.requestId, 2, "Booking failed - time conflict");
        }
    }
    
    /**
     * 处理修改预订请求
     */
    public MessageSerializer.ResponseMessage handleChange(MessageSerializer.RequestMessage request, 
                                                         ClientInfo clientInfo) throws Exception {
        
        ByteBuffer buffer = ByteBuffer.wrap(request.payload);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        // 解析修改参数
        String confirmId = MessageSerializer.deserializeString(buffer);
        int offsetMin = buffer.getInt();
        
        // 验证确认ID
        int appointmentId;
        try {
            appointmentId = Integer.parseInt(confirmId);
        } catch (NumberFormatException e) {
            return createErrorResponse(request.header.requestId, 3, "Invalid confirmation ID: " + confirmId);
        }
        
        // 执行修改
        var handle = appointmentManager.getHandle(clientInfo);
        boolean success = handle.change(appointmentId, offsetMin);
        
        if (success) {
            return createSuccessResponse(request.header.requestId, "Change successful");
        } else {
            return createErrorResponse(request.header.requestId, 2, "Change failed - conflict or not found");
        }
    }
    
    /**
     * 处理监控请求
     */
    public MessageSerializer.ResponseMessage handleMonitor(MessageSerializer.RequestMessage request, 
                                                          ClientInfo clientInfo) throws Exception {
        
        ByteBuffer buffer = ByteBuffer.wrap(request.payload);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        // 解析监控参数
        String facilityName = MessageSerializer.deserializeString(buffer);
        int durationSec = buffer.getInt();
        int clientPort = buffer.getInt();
        
        // 验证设施名称
        if (!ValidFacilities.isValidFacility(facilityName)) {
            return createErrorResponse(request.header.requestId, 1, "Invalid facility: " + facilityName);
        }
        
        // 验证监控时长
        if (durationSec <= 0 || durationSec > 60 * 60 * 24 * 7) {
            return createErrorResponse(request.header.requestId, 3, "Invalid duration: " + durationSec);
        }
        ClientInfo updatedClientInfo = new ClientInfo(clientInfo.getIp(), clientPort);
        
        // 开始监控
        var handle = appointmentManager.getHandle(updatedClientInfo);
        handle.monitor(request.header.requestId ,facilityName, Duration.ofSeconds(durationSec));
        
        return createSuccessResponse(request.header.requestId, "Monitoring started for " + durationSec + " seconds");
    }
    
    /**
     * 处理取消预订请求
     */
    public MessageSerializer.ResponseMessage handleCancel(MessageSerializer.RequestMessage request, 
                                                         ClientInfo clientInfo) throws Exception {
        
        ByteBuffer buffer = ByteBuffer.wrap(request.payload);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        int appointmentId = buffer.getInt();
        
        var handle = appointmentManager.getHandle(clientInfo);
        boolean success = handle.cancel(appointmentId);
        
        if (success) {
            return createSuccessResponse(request.header.requestId, "Cancellation successful");
        } else {
            return createErrorResponse(request.header.requestId, 2, "Cancellation failed - not found or not authorized");
        }
    }
    
    /**
     * 处理签到请求
     */
    public MessageSerializer.ResponseMessage handleCheckIn(MessageSerializer.RequestMessage request, 
                                                          ClientInfo clientInfo) throws Exception {
        
        ByteBuffer buffer = ByteBuffer.wrap(request.payload);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        int appointmentId = buffer.getInt();
        
        var handle = appointmentManager.getHandle(clientInfo);
        boolean success = handle.checkIn(appointmentId);
        
        if (success) {
            return createSuccessResponse(request.header.requestId, "Check-in successful");
        } else {
            return createErrorResponse(request.header.requestId, 2, "Check-in failed - not found or not authorized");
        }
    }
    
    /**
     * 验证时间参数
     */
    private boolean isValidTime(int day, int hour, int min) {
        return day >= 1 && day <= 7 && 
               hour >= 0 && hour <= 23 && 
               min >= 0 && min <= 59;
    }
    
    /**
     * 创建成功响应
     */
    private MessageSerializer.ResponseMessage createSuccessResponse(int requestId, String message) {
        MessageSerializer.MessageHeader header = new MessageSerializer.MessageHeader(
            requestId, null, MessageSerializer.Semantics.AT_LEAST_ONCE, 0);
        return new MessageSerializer.ResponseMessage(header, 0, message, null);
    }
    
    /**
     * 创建错误响应
     */
    private MessageSerializer.ResponseMessage createErrorResponse(int requestId, int status, String message) {
        MessageSerializer.MessageHeader header = new MessageSerializer.MessageHeader(
            requestId, null, MessageSerializer.Semantics.AT_LEAST_ONCE, 0);
        return new MessageSerializer.ResponseMessage(header, status, message, null);
    }
}
