package net.s6103;

import java.time.*;

public class Appointment {
    private final int clientId;
    private final int appointmentId;
    private Instant beginTime;
    private int lastingSeconds;

    public  Appointment(int clientId, int appointmentId,  Instant beginTime, int lastingSeconds) {
        this.clientId = clientId;
        this.appointmentId = appointmentId;
        this.beginTime = beginTime;
        this.lastingSeconds = lastingSeconds;
    }

    public int getClientId() {return clientId;}
    public int getAppointmentId() {return appointmentId;}
    public Instant getBeginTime() {return beginTime;}
    public Instant getEndTime() {return beginTime.plusSeconds(lastingSeconds);}
    public int getLastingSeconds() {return lastingSeconds;}

    /**
     * Shift the appointment `second` seconds later.
     * @param seconds the time to Shift. Negative if shifted advance.
     */
    public void delay(int seconds) {
        beginTime = beginTime.plusSeconds(seconds);
    }
}
