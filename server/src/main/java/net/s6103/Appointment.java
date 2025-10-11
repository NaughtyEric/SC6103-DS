package net.s6103;

import java.time.*;
import java.util.ArrayList;

public class Appointment {
    private final ClientInfo clientInfo;
    private final int appointmentId;
    private final String facilityName;
    private Instant beginTime;
    private final int lastingSeconds;
    private final ArrayList<Instant> checkInRecords;

    public Appointment(ClientInfo clientInfo, int appointmentId, String facilityName, Instant beginTime, int lastingSeconds) {
        this.clientInfo = clientInfo;
        this.appointmentId = appointmentId;
        this.facilityName = facilityName;
        this.beginTime = beginTime;
        this.lastingSeconds = lastingSeconds;
        this.checkInRecords = new ArrayList<>();
    }

    public final ClientInfo getClientInfo() { return clientInfo; }
    public final int getAppointmentId() { return appointmentId; }
    public final Instant getBeginTime() { return beginTime; }
    public final Instant getEndTime() { return beginTime.plusSeconds(lastingSeconds); }
    public final String getFacilityName() { return facilityName; }
    public final int getLastingSeconds() { return lastingSeconds; }
    public final ArrayList<Instant> getCheckInRecords() { return checkInRecords; }

    /**
     * Shift the appointment `second` seconds later.
     * @param offsetMinutes the time to Shift. Negative if shifted advance.
     */
    public void delay(int offsetMinutes) {
        beginTime = beginTime.plusSeconds(offsetMinutes * 60L);
    }

    public final void checkIn(Instant time) {
        checkInRecords.add(time);
    }
}
