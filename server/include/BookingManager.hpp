#ifndef BOOKING_MANAGER_HPP
#define BOOKING_MANAGER_HPP
#include "ClientManager.hpp"


class BookingManager
{
    /**
    * 查询(string facility name, Date days)
    * 预约(string facility name, Date start, Date end)
    * 改变预约(uint booking id,int offset(minutes))
    * 监控(string facility name, uint monitor_interval)
    * 取消预约(uint booking id)
    * 签到(uint booking id)->non-idempotent
    */
private:
    ClientManager clientManager;
    void loadFromFile();
    void saveToFile();
public:
    BookingManager();
    ~BookingManager();


};


#endif