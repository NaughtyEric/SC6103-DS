#pragma once

#include <cstdint>
#include <functional>
#include <string>
#include <vector>

// Forward-declare Windows types to avoid including winsock in header
struct sockaddr_in;

namespace fbk {

class UdpClient {
public:
    UdpClient(const std::string& serverIp,
              uint16_t serverPort,
              uint32_t timeoutMs,
              uint32_t maxRetries,
              double lossProbability);
    ~UdpClient();

    bool initOk() const { return initOk_; }

    // Send request and wait for reply with retries, return true if got reply.
    bool sendRequestAwaitReply(const std::vector<uint8_t>& request,
                               std::vector<uint8_t>& replyOut,
                               bool verboseLog,
                               bool dropFirstReplyOnce);

    // Bind and receive packets for durationSeconds; callback on each packet.
    // Returns number of packets received.
    int recvLoop(uint32_t durationSeconds,
                 const std::function<void(const std::vector<uint8_t>&)>& onPacket,
                 bool verboseLog);

    uint16_t localPort() const { return localPort_; }

private:
    bool initWinsock();
    void cleanupWinsock();

private:
    std::string serverIp_;
    uint16_t serverPort_{};
    uint32_t timeoutMs_{};
    uint32_t maxRetries_{};
    double lossProbability_{}; // 0.0~1.0

    int sock_ = -1;
    bool initOk_ = false;
    uint16_t localPort_ = 0;
};

} // namespace fbk


