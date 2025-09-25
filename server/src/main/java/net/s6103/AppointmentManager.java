package net.s6103;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

public class AppointmentManager {
    private final Map<Integer, Appointment> appointments = new ConcurrentHashMap<>();
    public AppointmentManager() {}
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(); // appointment lock
    private final ReentrantLock idLock = new ReentrantLock(); // appointment id lock
    static private int nextAppointmentId = 1;

    public class AppointmentManagerHandle {
        final private int clientId;
        final private AppointmentManager _manager;

        public AppointmentManagerHandle(int clientId, AppointmentManager manager) {
            this.clientId = clientId;
            this._manager = manager;
        }

        final public Appointment[] query(String facilityName, LocalDate days) {
            var start = days.atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
            var end = days.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
            rwLock.readLock().lock();
            try {
                return _manager.appointments.values().stream()
                        .filter(appointment -> appointment.getFacilityName().equals(facilityName))
                        .filter(appointment -> appointment.getBeginTime().isBefore(end) &&
                                appointment.getEndTime().isAfter(start))
                        .toArray(Appointment[]::new);
            } finally {
                rwLock.readLock().unlock();
            }
        }

        final public int book(String facilityName, Instant start, Instant end) throws Exception {
            if (! ValidFacilities.isValidFacility(facilityName)) {
                throw new Exception("Invalid facility name");
            }
            int lastingSeconds = (int)(end.getEpochSecond() - start.getEpochSecond());
            int id = -1;
            if (lastingSeconds <= 0) {
                Logger.getGlobal().warning("Invalid time range");
                return -1;
            }
            boolean conflict = false;
            idLock.lock();   // lock appointment id
            try {
                var appointment = new Appointment(clientId, nextAppointmentId, facilityName, start, lastingSeconds);
                rwLock.writeLock().lock();  // lock appointments for writing
                try {
                    for (var existingAppointment : _manager.appointments.values()) {
                        if (existingAppointment.getFacilityName().equals(facilityName)) {
                            if (existingAppointment.getBeginTime().isBefore(appointment.getEndTime()) &&
                                    existingAppointment.getEndTime().isAfter(appointment.getBeginTime())) {
                                conflict = true;
                                break;
                            }
                        }
                    }
                    if (!conflict) {
                        id = nextAppointmentId; // save id to return
                        _manager.appointments.put(id, appointment);
                        nextAppointmentId++;
                    }
                } finally {
                    rwLock.writeLock().unlock(); // release appointment write lock
                }
            } finally {
                idLock.unlock(); // release appointment id lock
            }
            if (conflict) {
                Logger.getGlobal().info("Booking conflict");
            } else {
                Logger.getGlobal().info("Booking successful: " + id);
            }
            return id;
        }

        final public boolean change(int appointmentId, int offsetMinutes) {
            boolean result = false;
            rwLock.writeLock().lock();
            try {
                var appointment = _manager.appointments.get(appointmentId);
                if (appointment == null) {
                    Logger.getGlobal().warning("No such appointment: " + appointmentId);
                } else if (appointment.getClientId() != clientId) {
                    Logger.getGlobal().warning("Client " + clientId + " trying to change appointment of client " + appointment.getClientId());
                } else {
                    var newBeginTime = appointment.getBeginTime().plusSeconds(offsetMinutes * 60);
                    var newEndTime = appointment.getEndTime().plusSeconds(offsetMinutes * 60);
                    boolean conflict = false;
                    for (var existingAppointment : _manager.appointments.values()) {
                        if (existingAppointment.getAppointmentId() != appointmentId &&
                                existingAppointment.getFacilityName().equals(appointment.getFacilityName())) {
                            if (existingAppointment.getBeginTime().isBefore(newEndTime) &&
                                    existingAppointment.getEndTime().isAfter(newBeginTime)) {
                                conflict = true;
                                break;
                            }
                        }
                    }
                    if (!conflict) {
                        appointment.delay(offsetMinutes);
                        Logger.getGlobal().info("Appointment " + appointmentId + " changed by client " + clientId);
                        result = true;
                    } else {
                        Logger.getGlobal().info("Change conflict for appointment " + appointmentId + " by client " + clientId);
                    }
                }
            } finally {
                rwLock.writeLock().unlock();
            }
            return result;
        }

        final public boolean cancel(int appointmentId) {
            boolean result = false;
            rwLock.writeLock().lock();
            try {
                var appointment = _manager.appointments.get(appointmentId);
                if (appointment == null) {
                    Logger.getGlobal().warning("No such appointment: " + appointmentId);
                }else if (appointment.getClientId() != clientId) {
                    Logger.getGlobal().warning("Client " + clientId + " trying to cancel appointment of client " + appointment.getClientId());
                } else {
                    _manager.appointments.remove(appointmentId);
                    Logger.getGlobal().info("Appointment " + appointmentId + " cancelled by client " + clientId);
                    result = true;
                }
            } finally {
                rwLock.writeLock().unlock();
            }
            return result;
        }
        
        /* TODO: 监控(string facility name, uint monitor_interval) */

        final public boolean checkIn(int appointmentId) {
            return false;
        }
    }

    /**
     * Get a handle for a specific client
     * @param clientId the client ID
     * @return the handle
     */
    public AppointmentManagerHandle getHandle(int clientId) {
        return new AppointmentManagerHandle(clientId, this);
    }

}

