package net.s6103;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public class ServerUdpThread extends Thread {
    static final int BUFFER_SIZE = 4096;
    static final int MAGIC = 0x46424B31; // 'FBK1'
    static final int VERSION = 1;

    private final int port;
    private final ClientManager manager;
    private final ExecutorService executor;
    private static final int THREAD_MAXIMUM = 10;
    private final AppointmentManager appointmentManager;

    public ServerUdpThread(int port) {
        this.port = port;
        this.manager = new ClientManager();
        this.executor = Executors.newFixedThreadPool(THREAD_MAXIMUM);
        this.appointmentManager = new AppointmentManager();
    }

    public ServerUdpThread(int port, ClientManager manager) {
        this.port = port;
        this.manager = manager;
        this.executor = Executors.newFixedThreadPool(THREAD_MAXIMUM);
        this.appointmentManager = new AppointmentManager();
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            System.out.println("UDP Server started on port " + port);

            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    // Process received data packet
                    byte[] data = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());

                    // Parse request and generate response
                    byte[] response = processRequest(data, packet.getAddress(), packet.getPort());

                    if (response != null) {
                        DatagramPacket responsePacket = new DatagramPacket(
                            response, response.length,
                            packet.getAddress(), packet.getPort()
                        );
                        socket.send(responsePacket);
                    }
                } catch (Exception e) {
                    System.err.println("Error processing request: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private byte[] processRequest(byte[] data, InetAddress clientAddress, int clientPort) {
        try {
            if (data.length < 24) { // Minimum message header length
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

            // Validate magic number and version
            if (magic != MAGIC) {
                return createErrorResponse(4, "Invalid magic number");
            }
            if (version != VERSION) {
                return createErrorResponse(4, "Unsupported version");
            }

            // Create client info
            ClientInfo clientInfo = new ClientInfo(clientAddress, clientPort);
            manager.addClient(clientInfo);

            // Handle different types of requests
            return switch (opCode) {
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
        } catch (Exception e) {
            System.err.println("Error parsing request: " + e.getMessage());
            return createErrorResponse(4, "Server error");
        }
    }

    private byte[] handleQueryAvailability(ByteBuffer buffer, int payloadLen, int requestId) {
        try {
            if (payloadLen < 4) {
                return createErrorResponse(3, "Invalid payload");
            }

            // 读取设施名称
            int facilityNameLen = buffer.getInt();
            if (facilityNameLen < 0 || facilityNameLen > payloadLen - 4) {
                return createErrorResponse(3, "Invalid facility name length");
            }

            byte[] facilityNameBytes = new byte[facilityNameLen];
            buffer.get(facilityNameBytes);
            String facilityName = new String(facilityNameBytes, StandardCharsets.UTF_8);

            // 读取查询天数
            int dayCount = buffer.getInt();
            if (dayCount < 1 || dayCount > 7) {
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
            return createErrorResponse(4, "Query error: " + e.getMessage());
        }
    }

    private byte[] handleBook(ByteBuffer buffer, int payloadLen, int requestId, ClientInfo clientInfo) {
        try {
            // 解析预订参数
            int facilityNameLen = buffer.getInt();
            byte[] facilityNameBytes = new byte[facilityNameLen];
            buffer.get(facilityNameBytes);
            String facilityName = new String(facilityNameBytes, StandardCharsets.UTF_8);

            int startDay = buffer.getInt();
            int startHour = buffer.getInt();
            int startMin = buffer.getInt();
            int endDay = buffer.getInt();
            int endHour = buffer.getInt();
            int endMin = buffer.getInt();

            // 计算时间
            LocalDate startDate = LocalDate.now().plusDays(startDay - 1);
            Instant startTime = startDate.atTime(startHour, startMin).toInstant(ZoneOffset.UTC);
            LocalDate endDate = LocalDate.now().plusDays(endDay - 1);
            Instant endTime = endDate.atTime(endHour, endMin).toInstant(ZoneOffset.UTC);

            // 执行预订
            var handle = appointmentManager.getHandle(clientInfo);
            int appointmentId = handle.book(facilityName, startTime, endTime);

            if (appointmentId > 0) {
                return createSuccessResponse(requestId, "Booking successful, ID: " + appointmentId);
            } else {
                return createErrorResponse(2, "Booking failed - time conflict");
            }

        } catch (Exception e) {
            return createErrorResponse(4, "Booking error: " + e.getMessage());
        }
    }

    private byte[] handleChange(ByteBuffer buffer, int payloadLen, int requestId, ClientInfo clientInfo) {
        try {
            // 解析修改参数
            int confirmIdLen = buffer.getInt();
            byte[] confirmIdBytes = new byte[confirmIdLen];
            buffer.get(confirmIdBytes);
            String confirmId = new String(confirmIdBytes, StandardCharsets.UTF_8);

            int offsetMin = buffer.getInt();

            // 执行修改
            var handle = appointmentManager.getHandle(clientInfo);
            int appointmentId = Integer.parseInt(confirmId);
            boolean success = handle.change(appointmentId, offsetMin);

            if (success) {
                return createSuccessResponse(requestId, "Change successful");
            } else {
                return createErrorResponse(2, "Change failed - conflict or not found");
            }

        } catch (Exception e) {
            return createErrorResponse(4, "Change error: " + e.getMessage());
        }
    }

    private byte[] handleMonitor(ByteBuffer buffer, int payloadLen, int requestId, ClientInfo clientInfo) {
        try {
            // 解析监控参数
            int facilityNameLen = buffer.getInt();
            byte[] facilityNameBytes = new byte[facilityNameLen];
            buffer.get(facilityNameBytes);
            String facilityName = new String(facilityNameBytes, StandardCharsets.UTF_8);

            int durationSec = buffer.getInt();
            int clientPort = buffer.getInt();

            // Start monitoring
            var handle = appointmentManager.getHandle(clientInfo);
            handle.monitor(facilityName, java.time.Duration.ofSeconds(durationSec));

            return createSuccessResponse(requestId, "Monitoring started");

        } catch (Exception e) {
            return createErrorResponse(4, "Monitor error: " + e.getMessage());
        }
    }

    private byte[] handleCancel(ByteBuffer buffer, int payloadLen, int requestId, ClientInfo clientInfo) {
        try {
            int appointmentId = buffer.getInt();

            var handle = appointmentManager.getHandle(clientInfo);
            boolean success = handle.cancel(appointmentId);

            if (success) {
                return createSuccessResponse(requestId, "Cancellation successful");
            } else {
                return createErrorResponse(2, "Cancellation failed - not found or not authorized");
            }

        } catch (Exception e) {
            return createErrorResponse(4, "Cancel error: " + e.getMessage());
        }
    }

    private byte[] handleCheckIn(ByteBuffer buffer, int payloadLen, int requestId, ClientInfo clientInfo) {
        try {
            int appointmentId = buffer.getInt();

            var handle = appointmentManager.getHandle(clientInfo);
            boolean success = handle.checkIn(appointmentId);

            if (success) {
                return createSuccessResponse(requestId, "Check-in successful");
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
            int totalLen = 24 + 4 + 4 + messageBytes.length; // header + status + messageLen + message

            ByteBuffer buffer = ByteBuffer.allocate(totalLen);
            buffer.order(ByteOrder.BIG_ENDIAN);

            // Message header
            buffer.putInt(MAGIC);
            buffer.putInt(VERSION);
            buffer.putInt(requestId);
            buffer.putInt(0); // opCode (response)
            buffer.putInt((int)(System.currentTimeMillis() / 1000)); // timestamp
            buffer.putInt(0); // semantics
            buffer.putInt(4 + 4 + messageBytes.length); // payloadLen

            // Status and message
            buffer.putInt(status);
            buffer.putInt(messageBytes.length);
            buffer.put(messageBytes);

            return buffer.array();
        } catch (Exception e) {
            System.err.println("Error creating response: " + e.getMessage());
            return null;
        }
    }
}
