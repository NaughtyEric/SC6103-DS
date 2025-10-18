package net.s6103;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

public class AppointmentManager {
    // mapping from appointment id to appointment
    private final Map<Integer, Appointment> appointments = new ConcurrentHashMap<>();
    // mapping from facility name to list of monitors
    private final Map<String, List<Monitor>> facilityMonitors = new ConcurrentHashMap<>();
    // scheduler for monitor notifications
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    static private int nextAppointmentId = 1;
    // lock for appointments
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    // lock for appointment id
    private final ReentrantLock appointmentIdLock = new ReentrantLock();

    public AppointmentManager() {
        // periodic cleanup task: remove expired monitors every minute
        scheduler.scheduleAtFixedRate(this::cleanupExpiredMonitors, 1, 60, TimeUnit.SECONDS);
    }

    public class AppointmentManagerHandle {
        private final ClientInfo clientInfo;
        private final AppointmentManager _manager;

        public AppointmentManagerHandle(ClientInfo clientInfo, AppointmentManager manager) {
            this.clientInfo = clientInfo;
            this._manager = manager;
        }

        public Appointment[] query(String facilityName, LocalDate day) {
            var start = day.atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
            var end = day.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
            rwLock.readLock().lock();
            try {
                return _manager.appointments.values().stream()
                        .filter(a -> a.getFacilityName().equals(facilityName))
                        .filter(a -> a.getBeginTime().isBefore(end) && a.getEndTime().isAfter(start))
                        .toArray(Appointment[]::new);
            } finally {
                rwLock.readLock().unlock();
            }
        }

        public int book(String facilityName, Instant start, Instant end) throws Exception {
            if (!ValidFacilities.isValidFacility(facilityName)) {
                throw new Exception("Invalid facility name");
            }
            int lastingSeconds = (int) (end.getEpochSecond() - start.getEpochSecond());
            if (lastingSeconds <= 0) {
                Logger.getGlobal().warning("Invalid time range");
                return -1;
            }
            int id = -1;
            boolean conflict = false;
            appointmentIdLock.lock();
            try {
                var appointment = new Appointment(clientInfo, nextAppointmentId, facilityName, start, lastingSeconds);
                rwLock.writeLock().lock();
                try {
                    for (var existing : _manager.appointments.values()) {
                        if (existing.getFacilityName().equals(facilityName)) {
                            if (existing.getBeginTime().isBefore(appointment.getEndTime())
                                    && existing.getEndTime().isAfter(appointment.getBeginTime())) {
                                conflict = true;
                                break;
                            }
                        }
                    }
                    if (!conflict) {
                        id = nextAppointmentId;
                        _manager.appointments.put(id, appointment);
                        nextAppointmentId++;
                    }
                } finally {
                    rwLock.writeLock().unlock();
                }
            } finally {
                appointmentIdLock.unlock();
            }
            if (conflict) {
                Logger.getGlobal().info("Booking conflict for client " + clientInfo);
            } else {
                Logger.getGlobal().info("Booking successful: " + id + " by " + clientInfo);
                notifyClients(facilityName, start, end, "BOOK");
            }
            return id;
        }

        public boolean change(int appointmentId, int offsetMinutes) {
            boolean result = false;
            rwLock.writeLock().lock();
            try {
                var appointment = _manager.appointments.get(appointmentId);
                if (appointment == null) {
                    Logger.getGlobal().warning("No such appointment: " + appointmentId);
                } else if (!appointment.getClientInfo().equals(clientInfo)) {
                    Logger.getGlobal().warning("Client " + clientInfo + " trying to change appointment of " + appointment.getClientInfo());
                } else {
                    var newBeginTime = appointment.getBeginTime().plusSeconds(offsetMinutes * 60L);
                    var newEndTime = appointment.getEndTime().plusSeconds(offsetMinutes * 60L);
                    boolean conflict = false;
                    for (var existing : _manager.appointments.values()) {
                        if (existing.getAppointmentId() != appointmentId &&
                                existing.getFacilityName().equals(appointment.getFacilityName())) {
                            if (existing.getBeginTime().isBefore(newEndTime)
                                    && existing.getEndTime().isAfter(newBeginTime)) {
                                conflict = true;
                                break;
                            }
                        }
                    }
                    if (!conflict) {
                        appointment.delay(offsetMinutes);
                        Logger.getGlobal().info("Appointment " + appointmentId + " changed by " + clientInfo);
                        result = true;
                        notifyClients(appointment.getFacilityName(), newBeginTime, newEndTime, "CHANGE");
                    } else {
                        Logger.getGlobal().info("Change conflict for appointment " + appointmentId + " by " + clientInfo);
                    }
                }
            } finally {
                rwLock.writeLock().unlock();
            }
            return result;
        }

        public boolean cancel(int appointmentId) {
            boolean result = false;
            rwLock.writeLock().lock();
            try {
                var appointment = _manager.appointments.get(appointmentId);
                if (appointment == null) {
                    Logger.getGlobal().warning("No such appointment: " + appointmentId);
                } else if (!appointment.getClientInfo().equals(clientInfo)) {
                    Logger.getGlobal().warning("Client " + clientInfo + " trying to cancel appointment of " + appointment.getClientInfo());
                } else {
                    _manager.appointments.remove(appointmentId);
                    Logger.getGlobal().info("Appointment " + appointmentId + " cancelled by " + clientInfo);
                    result = true;
                    notifyClients(appointment.getFacilityName(), appointment.getBeginTime(), appointment.getEndTime(), "CANCEL");
                }
            } finally {
                rwLock.writeLock().unlock();
            }
            return result;
        }

        public void monitor(String facilityName, Duration monitorInterval) {
            if (!ValidFacilities.isValidFacility(facilityName)) {
                Logger.getGlobal().warning("Invalid facility name: " + facilityName);
                return;
            }
            var monitor = new Monitor(clientInfo, monitorInterval);
            _manager.facilityMonitors
                    .computeIfAbsent(facilityName, _ -> new CopyOnWriteArrayList<>())
                    .add(monitor);
            Logger.getGlobal().info(clientInfo + " starts monitoring " + facilityName + " for " + monitorInterval.toMinutes() + " minutes.");
        }

        public boolean checkIn(int appointmentId) {
            boolean result = false;
            rwLock.writeLock().lock();
            try {
                var appointment = _manager.appointments.get(appointmentId);
                if (appointment == null) {
                    Logger.getGlobal().warning("No such appointment: " + appointmentId);
                } else if (!appointment.getClientInfo().equals(clientInfo)) {
                    Logger.getGlobal().warning("Client " + clientInfo + " trying to check in appointment of " + appointment.getClientInfo());
                } else {
                    ZoneId localZone = ZoneId.systemDefault(); // 比如 Asia/Singapore
                    ZonedDateTime localNow = ZonedDateTime.now(localZone);
                    LocalTime localTime = localNow.toLocalTime();
                    LocalDate localDate = localNow.toLocalDate();
                    ZonedDateTime utcSameClock = ZonedDateTime.of(localDate, localTime, ZoneOffset.UTC);
                    var now = utcSameClock.toInstant();
                    if (now.isBefore(appointment.getBeginTime()) || now.isAfter(appointment.getEndTime())) {
                        Logger.getGlobal().warning("Check-in time out of range for appointment " + appointmentId + " by " + clientInfo);
                    } else {
                        appointment.checkIn(now);
                        Logger.getGlobal().info("Appointment " + appointmentId + " checked in by " + clientInfo);
                        result = true;
                    }
                }
            } finally {
                rwLock.writeLock().unlock();
            }
            return result;
        }
    }

    public AppointmentManagerHandle getHandle(ClientInfo client) {
        return new AppointmentManagerHandle(client, this);
    }

    private void cleanupExpiredMonitors() {
        for (var entry : facilityMonitors.entrySet()) {
            entry.getValue().removeIf(Monitor::isExpired);
        }
    }

    private void notifyClients(String facilityName, Instant beginTime, Instant endTime, String operation) {
        Logger.getGlobal().info("Notifying facility " + facilityName + " from " + beginTime + " to " + endTime);
        for (var monitor : facilityMonitors.getOrDefault(facilityName, List.of())) {
            Logger.getGlobal().info("Checking monitor for client " + monitor.getClient());
            if (monitor.isExpired()) {
                continue;
            }
            // 监控逻辑：只要监控还在有效期内，就通知所有该设施的新预订
            // 不需要检查时间重叠，因为监控的是"设施变化"而不是"特定时间段"
            Logger.getGlobal().info("Notifying client " + monitor.getClient() + " about new appointment in " + facilityName);
            ClientInfo client = monitor.getClient();
            try (DatagramSocket socket = new DatagramSocket()) {
                InetAddress address = client.getIp();
                int port = client.getPort();
                String message = String.format("New operation %s in %s from %s to %s", operation,
                        facilityName, beginTime, endTime);
                MessageSerializer.MessageHeader header = new MessageSerializer.MessageHeader(
                        0, null, MessageSerializer.Semantics.AT_LEAST_ONCE, message.length()
                );

                byte[] data = MessageSerializer.serializeResponse(
                        new MessageSerializer.ResponseMessage(header, 0, message, null)
                );
                var packet = new java.net.DatagramPacket(data, data.length, address, port);
                socket.send(packet);

                Logger.getGlobal().info("Monitor notification sent to " + client);

            } catch (Exception e) {
                Logger.getGlobal().warning("Failed to notify client " + client + ": " + e.getMessage());
            }
        }
    }
}
