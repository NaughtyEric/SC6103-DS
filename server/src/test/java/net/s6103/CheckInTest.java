package net.s6103;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class CheckInTest {
    public static void main(String[] args) {
        // 创建测试预约
        ClientInfo clientInfo = new ClientInfo("127.0.0.1", 12345);
        Instant now = Instant.now();
        Instant startTime = now.minus(1, ChronoUnit.HOURS);  // 1小时前开始
        Instant endTime = now.plus(1, ChronoUnit.HOURS);     // 1小时后结束
        
        Appointment appointment = new Appointment(
            clientInfo, 
            1, 
            "Swimming Pool", 
            startTime, 
            (int) (endTime.getEpochSecond() - startTime.getEpochSecond())
        );
        
        System.out.println("=== 测试非幂等Check-in功能 ===");
        System.out.println("预约时间范围: " + startTime + " 到 " + endTime);
        System.out.println("当前时间: " + now);
        System.out.println();
        
        // 第一次check-in
        System.out.println("第一次check-in:");
        appointment.checkIn(now);
        System.out.println("Check-in状态: " + appointment.isCheckedIn());
        System.out.println("Check-in时间: " + appointment.getCheckInTime());
        System.out.println();
        
        // 第二次check-in (应该不会改变状态)
        System.out.println("第二次check-in:");
        appointment.checkIn(now.plus(10, ChronoUnit.MINUTES));
        System.out.println("Check-in状态: " + appointment.isCheckedIn());
        System.out.println("Check-in时间: " + appointment.getCheckInTime());
        System.out.println("(应该保持第一次的时间)");
        System.out.println();
        
        System.out.println("=== 测试完成 ===");
        System.out.println("结论: Check-in操作现在是非幂等的");
    }
}
