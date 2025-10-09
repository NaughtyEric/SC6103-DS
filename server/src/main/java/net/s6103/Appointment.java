package net.s6103;

import java.security.KeyPair;
import java.time.*;
import java.util.ArrayList;

public class Appointment {
    private final ClientInfo clientInfo;
    private final int appointmentId;
    private final String facilityName;
    private Instant beginTime;
    private final int lastingSeconds;
    private boolean checkedIn = false;
    private Instant checkInTime;

    public Appointment(ClientInfo clientInfo, int appointmentId, String facilityName, Instant beginTime, int lastingSeconds) {
        this.clientInfo = clientInfo;
        this.appointmentId = appointmentId;
        this.facilityName = facilityName;
        this.beginTime = beginTime;
        this.lastingSeconds = lastingSeconds;
    }

    public final ClientInfo getClientInfo() { return clientInfo; }
    public final int getAppointmentId() { return appointmentId; }
    public final Instant getBeginTime() { return beginTime; }
    public final Instant getEndTime() { return beginTime.plusSeconds(lastingSeconds); }
    public final String getFacility() { return facilityName; }
    public final int getLastingSeconds() { return lastingSeconds; }
    public final boolean isCheckedIn() { return checkedIn; }
    public final Instant getCheckInTime() { return checkInTime; }

    /**
     * Shift the appointment `second` seconds later.
     * @param seconds the time to Shift. Negative if shifted advance.
     */
    public void delay(int seconds) {
        beginTime = beginTime.plusSeconds(seconds);
    }

    public final String getFacilityName() {
        return facilityName;
    }

    public final void checkIn(Instant time) {
        if (!checkedIn) {
            checkedIn = true;
            checkInTime = time;
        }
    }
}
