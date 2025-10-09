# 服务端重构架构说明

## 概述

本次重构将原有的单体UDP服务器代码重构为模块化、标准化的架构，提高了代码的可读性、可维护性和可扩展性。

## 架构组件

### 1. MessageSerializer (消息序列化器)
**文件**: `MessageSerializer.java`

**职责**:
- 提供标准化的消息序列化和反序列化功能
- 支持网络字节序（大端序）和UTF-8字符串编码
- 定义协议常量和消息结构
- 提供辅助方法用于常见数据类型的序列化

**主要特性**:
- 统一的错误处理机制
- 类型安全的操作码和语义枚举
- 支持请求和响应消息的完整序列化

### 2. RequestManager (请求管理器)
**文件**: `RequestManager.java`

**职责**:
- 实现请求去重机制（At-Most-Once语义）
- 支持At-Least-Once和At-Most-Once两种调用语义
- 提供请求缓存和过期清理
- 统一的请求处理接口

**主要特性**:
- 基于请求ID和时间戳的去重
- 自动清理过期请求缓存
- 支持自定义请求处理器

### 3. ClientManager (客户端管理器)
**文件**: `ClientManager.java`

**职责**:
- 管理客户端会话和连接状态
- 跟踪客户端活动时间
- 提供客户端查找和会话管理功能
- 自动清理过期会话

**主要特性**:
- 基于IP和端口的客户端识别
- 会话超时自动清理
- 客户端活动统计

### 4. BusinessLogicHandler (业务逻辑处理器)
**文件**: `BusinessLogicHandler.java`

**职责**:
- 处理具体的预约管理业务逻辑
- 解析请求参数并验证
- 调用AppointmentManager执行业务操作
- 生成标准化的响应消息

**主要特性**:
- 参数验证和错误处理
- 业务逻辑与网络层分离
- 统一的响应格式

### 5. ServerUdpThread (UDP服务器线程)
**文件**: `ServerUdpThread.java`

**职责**:
- 处理UDP网络通信
- 协调各个组件的协作
- 提供异步请求处理
- 管理服务器生命周期

**主要特性**:
- 异步请求处理
- 优雅的服务器关闭
- 完善的错误处理和日志记录

## 重构优势

### 1. 模块化设计
- 每个组件职责单一，易于理解和维护
- 组件间通过清晰的接口进行交互
- 便于单元测试和集成测试

### 2. 标准化序列化
- 统一的序列化/反序列化机制
- 网络字节序和字符编码标准化
- 类型安全的协议定义

### 3. 请求去重和重试
- 支持两种调用语义
- 自动去重避免重复处理
- 请求缓存提高性能

### 4. 会话管理
- 客户端连接状态跟踪
- 自动清理过期会话
- 活动统计和监控

### 5. 错误处理
- 分层的错误处理机制
- 详细的日志记录
- 优雅的错误恢复

### 6. 可扩展性
- 易于添加新的操作类型
- 支持自定义请求处理器
- 模块化设计便于功能扩展

## 使用示例

### 启动服务器
```java
ServerUdpThread server = new ServerUdpThread(9000);
server.start();
```

### 自定义客户端管理器
```java
ClientManager customClientManager = new ClientManager();
ServerUdpThread server = new ServerUdpThread(9000, customClientManager);
server.start();
```

### 获取服务器状态
```java
String status = server.getStatus();
System.out.println(status); // "Server running on port 9000, active clients: 5"
```

## 测试

项目包含完整的单元测试和集成测试：

- `ServerIntegrationTest.java`: 集成测试
- `AppointmentManagerTest.java`: 业务逻辑测试

运行测试：
```bash
mvn test
```

## 配置

### 日志配置
```java
Logger.getLogger("net.s6103").setLevel(Level.INFO);
```

### 会话超时配置
在`ClientManager`中修改`SESSION_TIMEOUT_SECONDS`常量。

### 请求缓存配置
在`RequestManager`中修改`CACHE_EXPIRY_SECONDS`常量。

## 性能优化

1. **异步处理**: 使用线程池异步处理请求
2. **连接复用**: 客户端会话管理避免重复连接
3. **请求缓存**: At-Most-Once语义的请求缓存
4. **内存管理**: 自动清理过期数据

## 兼容性

重构后的代码完全兼容原有的客户端协议，无需修改客户端代码即可使用。

## 未来扩展

1. **负载均衡**: 可以轻松添加负载均衡支持
2. **监控指标**: 可以添加详细的性能监控
3. **配置管理**: 可以添加外部配置文件支持
4. **集群支持**: 可以扩展为分布式架构
