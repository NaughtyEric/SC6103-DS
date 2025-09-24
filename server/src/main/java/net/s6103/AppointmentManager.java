package net.s6103;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AppointmentManager {
    private final Map<Integer, Appointment> appointments = new ConcurrentHashMap<>();
    public AppointmentManager() {}
    private ValidFacilities validFacilities;

    public class AppointmentManagerHandle {
        final private int clientId;
        final private AppointmentManager _manager;

        public AppointmentManagerHandle(int clientId, AppointmentManager manager) {
            this.clientId = clientId;
            this._manager = manager;
        }

        /* TODO: Implement the following methods */

    }

    /**
     * 查询(string facility name, Date days)
     * 预约(string facility name, Date start, Date end)
     * 改变预约(uint booking id,int offset(minutes))
     * 监控(string facility name, uint monitor_interval)
     * 取消预约(uint booking id)
     * 签到(uint booking id)
     */

    /**
     * Get a handle for a specific client
     * @param clientId the client ID
     * @return the handle
     */
    public AppointmentManagerHandle getHandle(int clientId) {
        return new AppointmentManagerHandle(clientId, this);
    }

}

