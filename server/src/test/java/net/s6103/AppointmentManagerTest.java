package net.s6103;

import org.junit.jupiter.api.*;
import java.time.*;
import java.net.*;
import static org.junit.jupiter.api.Assertions.*;

public class AppointmentManagerTest {

    private AppointmentManager manager;
    private AppointmentManager.AppointmentManagerHandle handleA;
    private AppointmentManager.AppointmentManagerHandle handleB;
    private ClientInfo clientA;
    private ClientInfo clientB;

    @BeforeEach
    void setup() throws UnknownHostException {
        manager = new AppointmentManager();
        clientA = new ClientInfo(InetAddress.getByName("127.0.0.1"), 1001);
        clientB = new ClientInfo(InetAddress.getByName("127.0.0.1"), 1002);
        handleA = manager.getHandle(clientA);
        handleB = manager.getHandle(clientB);
    }

    @Test
    void testBookSuccess() throws Exception {
        Instant start = Instant.now().plusSeconds(60);
        Instant end = start.plusSeconds(3600);
        int id = handleA.book("Tennis Court 1", start, end);
        assertTrue(id > 0, "Booking should return a valid id");
    }

    @Test
    void testBookingConflict() throws Exception {
        Instant start = Instant.now().plusSeconds(60);
        Instant end = start.plusSeconds(3600);
        int id1 = handleA.book("Tennis Court 1", start, end);
        int id2 = handleB.book("Tennis Court 1", start.plusSeconds(1200), end.plusSeconds(1200));
        assertTrue(id1 > 0);
        assertEquals(-1, id2, "Should reject booking due to time conflict");
    }

    @Test
    void testChangeAppointment() throws Exception {
        Instant start = Instant.now().plusSeconds(60);
        Instant end = start.plusSeconds(3600);
        int id = handleA.book("Tennis Court 1", start, end);
        boolean changed = handleA.change(id, 30); // shift by 30 minutes
        assertTrue(changed, "Owner should be able to change their own appointment");
    }

    @Test
    void testChangeByOtherClientShouldFail() throws Exception {
        Instant start = Instant.now().plusSeconds(60);
        Instant end = start.plusSeconds(3600);
        int id = handleA.book("Tennis Court 1", start, end);
        boolean changed = handleB.change(id, 15);
        assertFalse(changed, "Other client should not be able to change appointment");
    }

    @Test
    void testChangeToConflictShouldFail() throws Exception {
        Instant start1 = Instant.now().plusSeconds(60);
        Instant end1 = start1.plusSeconds(3600);
        int id1 = handleA.book("Tennis Court 1", start1, end1);

        Instant start2 = start1.plusSeconds(7200); // 2 hours later
        Instant end2 = start2.plusSeconds(3600);
        int id2 = handleA.book("Tennis Court 1", start2, end2);

        boolean changed = handleA.change(id2, -120); // try to shift back by 120 minutes, causing conflict
        assertFalse(changed, "Should not allow change that causes conflict");
    }

    @Test
    void testCancelAppointment() throws Exception {
        Instant start = Instant.now().plusSeconds(60);
        Instant end = start.plusSeconds(3600);
        int id = handleA.book("Tennis Court 1", start, end);
        boolean cancelled = handleA.cancel(id);
        assertTrue(cancelled, "Owner should be able to cancel");
    }

    @Test
    void testCancelByOtherClientShouldFail() throws Exception {
        Instant start = Instant.now().plusSeconds(60);
        Instant end = start.plusSeconds(3600);
        int id = handleA.book("Tennis Court 1", start, end);
        boolean cancelled = handleB.cancel(id);
        assertFalse(cancelled, "Other client should not cancel others' appointments");
    }

    @Test
    void testQueryAppointments() throws Exception {
        LocalDate today = LocalDate.now();
        Instant start = today.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(600);
        Instant end = start.plusSeconds(3600);
        handleA.book("Tennis Court 1", start, end);
        Appointment[] results = handleA.query("Tennis Court 1", today);
        assertEquals(1, results.length, "Should find one appointment");
        assertEquals("Tennis Court 1", results[0].getFacilityName());
        assertEquals(start, results[0].getBeginTime());
        assertEquals(end, results[0].getEndTime());
        assertEquals(clientA, results[0].getClientInfo());
        assertEquals(3600, results[0].getLastingSeconds());
    }

    @Test
    void testCheckInWithinTimeRange() throws Exception {
        Instant start = Instant.now().plusSeconds(5);
        Instant end = start.plusSeconds(60);
        int id = handleA.book("Tennis Court 1", start, end);
        Thread.sleep(10000); // wait until check-in time window
        boolean result = handleA.checkIn(id);
        assertTrue(result, "Should be able to check in during valid time");
        Appointment[] results = handleA.query("Tennis Court 1", LocalDate.now());
        var record = results[0].getCheckInRecords();
        assertEquals(1, record.size(), "Check-in record should be logged");
    }

    @AfterEach
    void tearDown() {
        manager = null;
    }
}