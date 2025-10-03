package net.s6103;

import java.time.*;

public class Appointment {
    private final ClientInfo clientInfo;
    private final int appointmentId;
    private final String facilityName;
    private Instant beginTime;
    private int lastingSeconds;

    public Appointment(ClientInfo clientId, int appointmentId, String facilityName, Instant beginTime, int lastingSeconds) {
        this.clientInfo = clientId;
        this.appointmentId = appointmentId;
        this.facilityName = facilityName;
        this.beginTime = beginTime;
        this.lastingSeconds = lastingSeconds;
    }

    public final ClientInfo getClientInfo() {return clientInfo;}
    public final int getAppointmentId() {return appointmentId;}
    public final Instant getBeginTime() {return beginTime;}
    public final Instant getEndTime() {return beginTime.plusSeconds(lastingSeconds);}
    public final String getFacility() {return facilityName;}
    public final int getLastingSeconds() {return lastingSeconds;}

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
}
