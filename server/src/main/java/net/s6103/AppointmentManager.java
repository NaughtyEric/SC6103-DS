package net.s6103;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
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
                    // 预约不存在，但返回成功（幂等）
                    Logger.getGlobal().info("Appointment " + appointmentId + " already cancelled or not found");
                    result = true;
                } else if (!appointment.getClientInfo().equals(clientInfo)) {
                    Logger.getGlobal().warning("Client " + clientInfo + " trying to cancel appointment of " + appointment.getClientInfo());
                } else {
                    _manager.appointments.remove(appointmentId);
                    Logger.getGlobal().info("Appointment " + appointmentId + " cancelled by " + clientInfo);
                    result = true;
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
            _manager.facilityMonitors.get(facilityName).removeIf(m -> m.getClient().equals(clientInfo));
            Logger.getGlobal().info("Monitor expired for client " + clientInfo + " on facility " + facilityName);
        }

        public int checkIn(int appointmentId) {
            int result = 0; // 0 = success, 1 = already checked in, 2 = timeout, 3 = other errors
            rwLock.writeLock().lock();
            try {
                var appointment = _manager.appointments.get(appointmentId);
                if (appointment == null) {
                    Logger.getGlobal().warning("No such appointment: " + appointmentId);
                    result = 3; // other errors
                } else if (!appointment.getClientInfo().equals(clientInfo)) {
                    Logger.getGlobal().warning("Client " + clientInfo + " trying to check in appointment of " + appointment.getClientInfo());
                    Logger.getGlobal().warning("ClientInfo equals check: " + appointment.getClientInfo().equals(clientInfo));
                    Logger.getGlobal().warning("ClientInfo hashCode: " + appointment.getClientInfo().hashCode() + " vs " + clientInfo.hashCode());
                    result = 3; // other errors
                } else if (appointment.isCheckedIn()) {
                    Logger.getGlobal().warning("Appointment " + appointmentId + " already checked in by " + clientInfo);
                    result = 1; // already checked in
                } else {
                    var now = Instant.now();
                    if (now.isBefore(appointment.getBeginTime()) || now.isAfter(appointment.getEndTime())) {
                        Logger.getGlobal().warning("Check-in time out of range for appointment " + appointmentId + " by " + clientInfo);
                        result = 2; // timeout
                    } else {
                        appointment.checkIn(now);
                        Logger.getGlobal().info("Appointment " + appointmentId + " checked in by " + clientInfo);
                        result = 0; // success
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
}
