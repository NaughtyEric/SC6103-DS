#include "protocol.h"

#include <cstring>
#include <ctime>

namespace fbk {

static void appendBytes(std::vector<uint8_t>& buf, const void* data, size_t len) {
    const uint8_t* p = static_cast<const uint8_t*>(data);
    buf.insert(buf.end(), p, p + len);
}

static uint32_t hton32(uint32_t v) {
    uint8_t b[4];
    b[0] = static_cast<uint8_t>((v >> 24) & 0xFF);
    b[1] = static_cast<uint8_t>((v >> 16) & 0xFF);
    b[2] = static_cast<uint8_t>((v >> 8) & 0xFF);
    b[3] = static_cast<uint8_t>((v) & 0xFF);
    uint32_t out;
    std::memcpy(&out, b, 4);
    return out;
}

static uint32_t ntoh32(uint32_t v) {
    const uint8_t* b = reinterpret_cast<const uint8_t*>(&v);
    return (static_cast<uint32_t>(b[0]) << 24) |
           (static_cast<uint32_t>(b[1]) << 16) |
           (static_cast<uint32_t>(b[2]) << 8)  |
           (static_cast<uint32_t>(b[3]));
}

void appendUint32(std::vector<uint8_t>& buf, uint32_t value) {
    uint32_t n = hton32(value);
    appendBytes(buf, &n, sizeof(n));
}

void appendInt32(std::vector<uint8_t>& buf, int32_t value) {
    uint32_t u = static_cast<uint32_t>(value);
    appendUint32(buf, u);
}

void appendString(std::vector<uint8_t>& buf, const std::string& s) {
    appendUint32(buf, static_cast<uint32_t>(s.size()));
    if (!s.empty()) {
        appendBytes(buf, s.data(), s.size());
    }
}

bool readUint32(const uint8_t*& p, size_t& remaining, uint32_t& out) {
    if (remaining < 4) return false;
    uint32_t n;
    std::memcpy(&n, p, 4);
    out = ntoh32(n);
    p += 4;
    remaining -= 4;
    return true;
}

bool readInt32(const uint8_t*& p, size_t& remaining, int32_t& out) {
    uint32_t u;
    if (!readUint32(p, remaining, u)) return false;
    out = static_cast<int32_t>(u);
    return true;
}

bool readString(const uint8_t*& p, size_t& remaining, std::string& out) {
    uint32_t len = 0;
    if (!readUint32(p, remaining, len)) return false;
    if (remaining < len) return false;
    out.assign(reinterpret_cast<const char*>(p), reinterpret_cast<const char*>(p) + len);
    p += len;
    remaining -= len;
    return true;
}

std::vector<uint8_t> buildRequest(uint32_t requestId,
                                  OpCode op,
                                  Semantics semantics,
                                  const std::vector<uint8_t>& payload) {
    std::vector<uint8_t> buf;
    // header has 7 uint32 fields (28 bytes) + payload
    buf.reserve(28 + payload.size());

    // Write header fields individually in big-endian (network order), 32-bit each
    appendUint32(buf, kMagic);
    appendUint32(buf, kVersion);
    appendUint32(buf, requestId);
    appendUint32(buf, static_cast<uint32_t>(op)); // opCode as 32-bit
    appendUint32(buf, static_cast<uint32_t>(time(nullptr))); // timestamp (seconds)
    appendUint32(buf, static_cast<uint32_t>(semantics));
    appendUint32(buf, static_cast<uint32_t>(payload.size()));

    // Append payload bytes
    if (!payload.empty()) {
        appendBytes(buf, payload.data(), payload.size());
    }
    return buf;
}

bool parseReply(const std::vector<uint8_t>& buffer,
                uint32_t& status,
                std::string& message,
                std::vector<uint8_t>& remainingPayload) {
    const uint8_t* p = buffer.data();
    size_t remaining = buffer.size();

    // Need at least 7 uint32 fields in header
    if (remaining < 28) return false;

    uint32_t magic = 0, version = 0, requestId = 0, opCode = 0, timestamp = 0, semantics = 0, payloadLen = 0;
    if (!readUint32(p, remaining, magic)) return false;
    if (!readUint32(p, remaining, version)) return false;
    if (!readUint32(p, remaining, requestId)) return false;
    if (!readUint32(p, remaining, opCode)) return false;
    if (!readUint32(p, remaining, timestamp)) return false;
    if (!readUint32(p, remaining, semantics)) return false;
    if (!readUint32(p, remaining, payloadLen)) return false;

    if (magic != kMagic) return false;
    if (version != kVersion) return false; // keep strict to catch mismatch
    // Response is encoded with opCode == 0 per server implementation
    if (opCode != 0) return false;
    // Ensure payload length is sane
    if (remaining < payloadLen) return false;

    // Parse payload: status(uint32) + message(string) + optional remaining bytes
    if (!readUint32(p, remaining, status)) return false;
    if (!readString(p, remaining, message)) return false;

    remainingPayload.assign(p, p + remaining);
    return true;
}

} // namespace fbk


