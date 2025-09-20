# 设施预订系统 - C++ 客户端

这是一个分布式设施预订系统的 C++ 客户端实现，使用 UDP 协议与 Java 服务端通信。

## 功能特性

- **四个必选操作**：

  1. 查询设施可用性（按天查询）
  2. 预订设施（指定时间段）
  3. 修改预订（时间偏移）
  4. 监控设施（阻塞式回调）

- **调用语义支持**：

  - At-Least-Once (ALO)：至少一次调用
  - At-Most-Once (AMO)：最多一次调用

- **网络特性**：
  - UDP 通信
  - 超时重试机制
  - 丢包仿真
  - 网络字节序转换

## 编译

### 环境要求

- Windows 10/11
- CMake 3.16+
- C++17 编译器（Visual Studio 2019+ 或 MinGW）
- Windows SDK

### 编译步骤

```bash
# 进入项目目录
cd client

# 生成构建文件
cmake -S . -B build

# 编译
cmake --build build --config Release
```

## 运行

### 基本用法

```bash
facility_client <server_ip> <server_port> <semantics> [loss_prob] [timeout_ms] [retries]
```

### 参数说明

- `server_ip`: 服务器 IP 地址
- `server_port`: 服务器端口号
- `semantics`: 调用语义 (`alo` 或 `amo`)
- `loss_prob`: 丢包概率 (0.0-1.0，默认 0.0)
- `timeout_ms`: 超时时间毫秒 (默认 800)
- `retries`: 重试次数 (默认 2)

### 运行示例

**At-Least-Once 语义（无丢包）**：

```bash
build\Release\facility_client.exe 192.168.1.10 9000 alo 0.0 800 2
```

**At-Most-Once 语义（30% 丢包）**：

```bash
build\Release\facility_client.exe 192.168.1.10 9000 amo 0.3 1000 3
```

## 协议格式

### 消息头（24 字节）

```
magic: uint32      // 魔数 0x46424B31 ('FBK1')
version: uint32    // 版本号 1
requestId: uint32  // 请求ID
opCode: uint32     // 操作码
semantics: uint32  // 调用语义 (0=ALO, 1=AMO)
payloadLen: uint32 // 载荷长度
```

### 操作码

- 1: QueryAvailability (查询可用性)
- 2: Book (预订)
- 3: Change (修改预订)
- 4: Monitor (监控)

### 载荷格式

**查询可用性**：

```
facilityName: string
dayCount: uint32
days: uint32[] (1-7 for Mon-Sun)
```

**预订设施**：

```
facilityName: string
startDay: uint32
startHour: uint32
startMin: uint32
endDay: uint32
endHour: uint32
endMin: uint32
```

**修改预订**：

```
confirmId: string
offsetMin: int32 (正数=延迟，负数=提前)
```

**监控设施**：

```
facilityName: string
durationSec: uint32
clientPort: uint32
```

### 回复格式

```
status: uint32     // 状态码
message: string    // 状态消息
[additional data]  // 额外数据（如可用性信息）
```

## 与 Java 服务端对接

### 字节序

- 所有整型数据使用网络字节序（大端序）
- 字符串格式：`[长度(uint32)] + [UTF-8字节]`

### 字段顺序

严格按照上述载荷格式的顺序发送和接收数据。

### 状态码约定

- 0: 成功
- 1: 设施不存在
- 2: 时间冲突
- 3: 无效参数
- 4: 系统错误

## 使用示例

1. **查询设施可用性**：

   - 选择菜单项 1
   - 输入设施名称（如 "Room101"）
   - 输入要查询的天数（1-7，0 结束）

2. **预订设施**：

   - 选择菜单项 2
   - 输入设施名称
   - 输入开始时间（天 时 分）
   - 输入结束时间（天 时 分）

3. **修改预订**：

   - 选择菜单项 3
   - 输入确认 ID
   - 输入时间偏移（分钟）

4. **监控设施**：
   - 选择菜单项 4
   - 输入设施名称
   - 输入监控时长（秒）
   - 程序将阻塞等待服务器回调

## 注意事项

- 监控期间程序会阻塞，无法进行其他操作
- 时间格式：天(1-7)，时(0-23)，分(0-59)
- 所有网络通信都经过超时和重试处理
- 丢包仿真仅用于测试，生产环境应设为 0.0
