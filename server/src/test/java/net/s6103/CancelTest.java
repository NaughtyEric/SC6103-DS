package net.s6103;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class CancelTest {
    public static void main(String[] args) {
        // 创建测试预约管理器
        AppointmentManager manager = new AppointmentManager();
        ClientInfo clientInfo = new ClientInfo("127.0.0.1", 12345);
        var handle = manager.getHandle(clientInfo);
        
        // 创建测试预约
        Instant now = Instant.now();
        Instant startTime = now.plus(1, ChronoUnit.HOURS);
        Instant endTime = now.plus(2, ChronoUnit.HOURS);
        
        System.out.println("=== 测试幂等Cancel操作 ===");
        
        try {
            // 创建预约
            int appointmentId = handle.book("Swimming Pool", startTime, endTime);
            System.out.println("创建预约 ID: " + appointmentId);
            System.out.println();
            
            // 第一次cancel
            System.out.println("第一次cancel:");
            boolean result1 = handle.cancel(appointmentId);
            System.out.println("结果: " + result1);
            System.out.println();
            
            // 第二次cancel (幂等测试)
            System.out.println("第二次cancel (幂等测试):");
            boolean result2 = handle.cancel(appointmentId);
            System.out.println("结果: " + result2);
            System.out.println();
            
            // 第三次cancel (幂等测试)
            System.out.println("第三次cancel (幂等测试):");
            boolean result3 = handle.cancel(appointmentId);
            System.out.println("结果: " + result3);
            System.out.println();
            
            // 验证幂等性
            System.out.println("=== 幂等性验证 ===");
            System.out.println("第一次cancel结果: " + result1);
            System.out.println("第二次cancel结果: " + result2);
            System.out.println("第三次cancel结果: " + result3);
            
            if (result1 == result2 && result2 == result3) {
                System.out.println("✓ Cancel操作是幂等的！");
            } else {
                System.out.println("✗ Cancel操作不是幂等的");
            }
            
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
