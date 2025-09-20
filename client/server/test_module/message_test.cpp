#include <gtest/gtest.h>
#include "message.hpp"

TEST(MessageParamTest, SerializeInt) {
    MessageParam param(42);
    std::string serialized = param.serialize();
    EXPECT_EQ(serialized.size(), 5);
    std::string expected("\xff\x2a\x00\x00\x00", 5);
    EXPECT_EQ(serialized, expected);
}
 
TEST(MessageParamTest, SerializeString) {
    MessageParam param("Hello");
    std::string serialized = param.serialize();
    EXPECT_EQ(serialized, "\x05Hello");
}

TEST(MessageParamTest, DeserializeInt) {
    MessageParam param;
    std::string src("\xff\x2a\x00\x00\x00", 5);
    size_t consumed = param.deserialize(src);
    EXPECT_EQ(consumed, 5);
    EXPECT_EQ(param.getValueInt(), 42);
}

TEST(MessageParamTest, DeserializeString) {
    MessageParam param;
    std::string src("\x05Hello");
    size_t consumed = param.deserialize(src);
    EXPECT_EQ(consumed, 6);
    EXPECT_EQ(param.getValueStr(), "Hello");
}
