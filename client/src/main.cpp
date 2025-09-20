#include "protocol.h"
#include "udp_client.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

using namespace fbk;

static void printUsage() {
    std::printf("Usage: facility_client <server_ip> <server_port> <semantics:alo|amo> [loss_prob=0.0] [timeout_ms=800] [retries=2]\n");
}

static Semantics parseSemantics(const std::string& s) {
    if (s == "amo") return Semantics::AtMostOnce;
    return Semantics::AtLeastOnce;
}

static uint32_t g_requestSeq = 1;
static uint32_t nextRequestId() { return g_requestSeq++; }

// Helper to send request and print result
static bool sendAndPrint(UdpClient& cli, OpCode op, const std::vector<uint8_t>& payload, Semantics sem) {
    auto req = buildRequest(nextRequestId(), op, sem, payload);
    std::vector<uint8_t> reply;
    if (!cli.sendRequestAwaitReply(req, reply, true)) {
        std::puts("No reply.");
        return false;
    }
    uint32_t status = 0; std::string msg; std::vector<uint8_t> rest;
    if (!parseReply(reply, status, msg, rest)) {
        std::puts("Bad reply format.");
        return false;
    }
    std::printf("Status: %u, Message: %s\n", status, msg.c_str());
    return true;
}

// 1. Query availability
static bool doQuery(UdpClient& cli, Semantics sem) {
    char facility[256];
    std::printf("Enter facility name: ");
    if (std::scanf("%255s", facility) != 1) return false;
    
    std::printf("Enter days (1-7 for Mon-Sun, space separated, 0 to finish): ");
    std::vector<uint32_t> days;
    int day;
    while (std::scanf("%d", &day) == 1 && day != 0) {
        if (day >= 1 && day <= 7) days.push_back(static_cast<uint32_t>(day));
    }
    
    std::vector<uint8_t> payload;
    appendString(payload, std::string(facility));
    appendUint32(payload, static_cast<uint32_t>(days.size()));
    for (uint32_t d : days) appendUint32(payload, d);
    
    return sendAndPrint(cli, OpCode::QueryAvailability, payload, sem);
}

// 2. Book facility
static bool doBook(UdpClient& cli, Semantics sem) {
    char facility[256];
    std::printf("Enter facility name: ");
    if (std::scanf("%255s", facility) != 1) return false;
    
    int startDay, startHour, startMin, endDay, endHour, endMin;
    std::printf("Enter start time (day hour min): ");
    if (std::scanf("%d %d %d", &startDay, &startHour, &startMin) != 3) return false;
    std::printf("Enter end time (day hour min): ");
    if (std::scanf("%d %d %d", &endDay, &endHour, &endMin) != 3) return false;
    
    std::vector<uint8_t> payload;
    appendString(payload, std::string(facility));
    appendUint32(payload, static_cast<uint32_t>(startDay));
    appendUint32(payload, static_cast<uint32_t>(startHour));
    appendUint32(payload, static_cast<uint32_t>(startMin));
    appendUint32(payload, static_cast<uint32_t>(endDay));
    appendUint32(payload, static_cast<uint32_t>(endHour));
    appendUint32(payload, static_cast<uint32_t>(endMin));
    
    return sendAndPrint(cli, OpCode::Book, payload, sem);
}

// 3. Change booking
static bool doChange(UdpClient& cli, Semantics sem) {
    char confirmId[256];
    std::printf("Enter confirmation ID: ");
    if (std::scanf("%255s", confirmId) != 1) return false;
    
    int offsetMin;
    std::printf("Enter offset in minutes (positive=delay, negative=advance): ");
    if (std::scanf("%d", &offsetMin) != 1) return false;
    
    std::vector<uint8_t> payload;
    appendString(payload, std::string(confirmId));
    appendInt32(payload, offsetMin);
    
    return sendAndPrint(cli, OpCode::Change, payload, sem);
}

// 4. Monitor facility
static bool doMonitor(UdpClient& cli, Semantics sem) {
    char facility[256];
    std::printf("Enter facility name: ");
    if (std::scanf("%255s", facility) != 1) return false;
    
    int durationSec;
    std::printf("Enter monitor duration in seconds: ");
    if (std::scanf("%d", &durationSec) != 1) return false;
    
    // Register for monitoring
    std::vector<uint8_t> payload;
    appendString(payload, std::string(facility));
    appendUint32(payload, static_cast<uint32_t>(durationSec));
    appendUint32(payload, cli.localPort()); // Our port for callbacks
    
    if (!sendAndPrint(cli, OpCode::Monitor, payload, sem)) {
        return false;
    }
    
    std::printf("Monitoring for %d seconds...\n", durationSec);
    int count = cli.recvLoop(static_cast<uint32_t>(durationSec), 
        [](const std::vector<uint8_t>& packet) {
            uint32_t status = 0; std::string msg; std::vector<uint8_t> rest;
            if (parseReply(packet, status, msg, rest)) {
                std::printf("[Callback] Status: %u, Message: %s\n", status, msg.c_str());
            }
        }, true);
    
    std::printf("Received %d callbacks during monitoring.\n", count);
    return true;
}

// 5. Cancel booking
static bool doCancel(UdpClient& cli, Semantics sem) {
    uint32_t bookingId;
    std::printf("Enter booking ID to cancel: ");
    if (std::scanf("%u", &bookingId) != 1) return false;
    
    std::vector<uint8_t> payload;
    appendUint32(payload, bookingId);
    
    return sendAndPrint(cli, OpCode::Cancel, payload, sem);
}

// 6. Check in (non-idempotent)
static bool doCheckIn(UdpClient& cli, Semantics sem) {
    uint32_t bookingId;
    std::printf("Enter booking ID to check in: ");
    if (std::scanf("%u", &bookingId) != 1) return false;
    
    std::vector<uint8_t> payload;
    appendUint32(payload, bookingId);
    
    return sendAndPrint(cli, OpCode::CheckIn, payload, sem);
}

int main(int argc, char** argv) {
    if (argc < 4) {
        printUsage();
        return 1;
    }
    std::string ip = argv[1];
    uint16_t port = static_cast<uint16_t>(std::atoi(argv[2]));
    Semantics sem = parseSemantics(argv[3]);
    double loss = (argc >= 5) ? std::atof(argv[4]) : 0.0;
    uint32_t timeoutMs = (argc >= 6) ? static_cast<uint32_t>(std::atoi(argv[5])) : 800;
    uint32_t retries = (argc >= 7) ? static_cast<uint32_t>(std::atoi(argv[6])) : 2;

    UdpClient client(ip, port, timeoutMs, retries, loss);
    if (!client.initOk()) {
        std::puts("Init failed.");
        return 2;
    }
    std::printf("Local UDP port: %u\n", client.localPort());

    while (true) {
        std::puts("\n--- Facility Client ---");
        std::puts("1) Query availability");
        std::puts("2) Book facility");
        std::puts("3) Change booking");
        std::puts("4) Monitor facility (blocking)");
        std::puts("5) Cancel booking");
        std::puts("6) Check in (non-idempotent)");
        std::puts("0) Exit");
        std::printf("Select: ");
        int choice = -1;
        if (std::scanf("%d", &choice) != 1) break;
        if (choice == 0) break;
        switch (choice) {
            case 1:
                doQuery(client, sem);
                break;
            case 2:
                doBook(client, sem);
                break;
            case 3:
                doChange(client, sem);
                break;
            case 4:
                doMonitor(client, sem);
                break;
            case 5:
                doCancel(client, sem);
                break;
            case 6:
                doCheckIn(client, sem);
                break;
            default:
                std::puts("Invalid choice.");
        }
    }
    return 0;
}


