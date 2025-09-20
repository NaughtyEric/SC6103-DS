#include "udp_client.h"

#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <random>
#include <string>
#include <thread>
#include <vector>

#include <winsock2.h>
#include <ws2tcpip.h>
typedef int socklen_t;

namespace fbk {

static uint16_t getSockLocalPort(int sock) {
    sockaddr_in addr{};
    socklen_t len = sizeof(addr);
    if (getsockname(sock, reinterpret_cast<sockaddr*>(&addr), &len) == 0) {
        return ntohs(addr.sin_port);
    }
    return 0;
}

UdpClient::UdpClient(const std::string& serverIp,
                     uint16_t serverPort,
                     uint32_t timeoutMs,
                     uint32_t maxRetries,
                     double lossProbability)
    : serverIp_(serverIp),
      serverPort_(serverPort),
      timeoutMs_(timeoutMs),
      maxRetries_(maxRetries),
      lossProbability_(lossProbability) {
    initOk_ = initWinsock();
}

UdpClient::~UdpClient() {
    if (sock_ != -1) {
        closesocket(sock_);
    }
    cleanupWinsock();
}

bool UdpClient::initWinsock() {
    WSADATA wsaData;
    int r = WSAStartup(MAKEWORD(2, 2), &wsaData);
    if (r != 0) {
        std::fprintf(stderr, "WSAStartup failed: %d\n", r);
        return false;
    }
    sock_ = static_cast<int>(socket(AF_INET, SOCK_DGRAM, 0));
    if (sock_ < 0) {
        std::perror("socket");
        return false;
    }

    // Bind to ephemeral port
    sockaddr_in local{};
    local.sin_family = AF_INET;
    local.sin_addr.s_addr = htonl(INADDR_ANY);
    local.sin_port = htons(0);
    if (bind(sock_, reinterpret_cast<sockaddr*>(&local), sizeof(local)) < 0) {
        std::perror("bind");
        return false;
    }

    // Set recv timeout
    DWORD tv = timeoutMs_;
    setsockopt(sock_, SOL_SOCKET, SO_RCVTIMEO, reinterpret_cast<const char*>(&tv), sizeof(tv));

    localPort_ = getSockLocalPort(sock_);
    return true;
}

void UdpClient::cleanupWinsock() {
    WSACleanup();
}

bool UdpClient::sendRequestAwaitReply(const std::vector<uint8_t>& request,
                                      std::vector<uint8_t>& replyOut,
                                      bool verboseLog) {
    sockaddr_in server{};
    server.sin_family = AF_INET;
    InetPton(AF_INET, std::wstring(serverIp_.begin(), serverIp_.end()).c_str(), &server.sin_addr);
    server.sin_port = htons(serverPort_);

    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_real_distribution<double> dist(0.0, 1.0);

    for (uint32_t attempt = 0; attempt <= maxRetries_; ++attempt) {
        double r = dist(gen);
        if (r >= lossProbability_) {
            int sent = sendto(sock_, reinterpret_cast<const char*>(request.data()), static_cast<int>(request.size()), 0,
                              reinterpret_cast<sockaddr*>(&server), sizeof(server));
            if (sent < 0) {
                std::perror("sendto");
            } else if (verboseLog) {
                std::printf("[send] %d bytes (attempt %u)\n", sent, attempt + 1);
            }
        } else if (verboseLog) {
            std::printf("[drop] simulated outgoing loss on attempt %u\n", attempt + 1);
        }

        // Wait for reply
        std::vector<uint8_t> buf(64 * 1024);
        sockaddr_in from{};
        socklen_t fromlen = sizeof(from);
        int n = recvfrom(sock_, reinterpret_cast<char*>(buf.data()), static_cast<int>(buf.size()), 0,
                         reinterpret_cast<sockaddr*>(&from), &fromlen);
        if (n > 0) {
            buf.resize(static_cast<size_t>(n));
            replyOut.swap(buf);
            return true;
        }
        // else timeout or error, retry
        if (verboseLog) {
            std::printf("[timeout] no reply, retrying (%u/%u)\n", attempt + 1, maxRetries_ + 1);
        }
    }
    return false;
}

int UdpClient::recvLoop(uint32_t durationSeconds,
                        const std::function<void(const std::vector<uint8_t>&)>& onPacket,
                        bool verboseLog) {
    auto start = std::chrono::steady_clock::now();
    int count = 0;
    while (true) {
        auto now = std::chrono::steady_clock::now();
        auto elapsed = std::chrono::duration_cast<std::chrono::seconds>(now - start).count();
        if (elapsed >= static_cast<long long>(durationSeconds)) break;

        // Adjust remaining timeout dynamically for the loop
        uint32_t leftMs = static_cast<uint32_t>((durationSeconds - elapsed) * 1000);
        DWORD tv = leftMs > 100 ? 100 : leftMs; // poll up to 100ms
        setsockopt(sock_, SOL_SOCKET, SO_RCVTIMEO, reinterpret_cast<const char*>(&tv), sizeof(tv));

        std::vector<uint8_t> buf(64 * 1024);
        sockaddr_in from{};
        socklen_t fromlen = sizeof(from);
        int n = recvfrom(sock_, reinterpret_cast<char*>(buf.data()), static_cast<int>(buf.size()), 0,
                         reinterpret_cast<sockaddr*>(&from), &fromlen);
        if (n > 0) {
            buf.resize(static_cast<size_t>(n));
            if (verboseLog) std::printf("[recv] %d bytes\n", n);
            onPacket(buf);
            ++count;
        }
        // else timeout, continue polling
    }
    return count;
}

} // namespace fbk


