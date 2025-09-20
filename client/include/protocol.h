#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace fbk {

static constexpr uint32_t kMagic = 0x46424B31; // 'FBK1'
static constexpr uint32_t kVersion = 1;

enum class OpCode : uint8_t {
    QueryAvailability = 1,
    Book = 2,
    Change = 3,
    Monitor = 4,
    Cancel = 5,
    CheckIn = 6,
};

enum class Semantics : uint32_t {
    AtLeastOnce = 0,
    AtMostOnce = 1,
};

struct MessageHeader {
    uint32_t magic;
    uint32_t version;
    uint32_t requestId;
    uint8_t commandId;    // char type as required
    uint32_t timestamp;   // uint32 as required
    uint32_t semantics;
    uint32_t payloadLen; // bytes count following this header
};

// Basic TLV-like helpers
void appendUint32(std::vector<uint8_t>& buf, uint32_t value);
void appendInt32(std::vector<uint8_t>& buf, int32_t value);
void appendString(std::vector<uint8_t>& buf, const std::string& s);

bool readUint32(const uint8_t*& p, size_t& remaining, uint32_t& out);
bool readInt32(const uint8_t*& p, size_t& remaining, int32_t& out);
bool readString(const uint8_t*& p, size_t& remaining, std::string& out);

// Build request buffer: [header][payload]
std::vector<uint8_t> buildRequest(uint32_t requestId,
                                  OpCode op,
                                  Semantics semantics,
                                  const std::vector<uint8_t>& payload);

// Parse reply assuming: [header][status(uint32)][message(string)][payload(optional...)]
// Returns true on success parsing basic parts.
bool parseReply(const std::vector<uint8_t>& buffer,
                uint32_t& status,
                std::string& message,
                std::vector<uint8_t>& remainingPayload);

} // namespace fbk


