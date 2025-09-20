#ifndef MESSAGE_HPP
#define MESSAGE_HPP

#include <string>
#include <vector>
#include "serializable.hpp"

extern const size_t INDICATOR_LENGTH;

/**
 * Represents a parameter in a message.
 * It can be a string or a integer.
 * 
 * 代表消息中的一个参数
 * 它可以是一个字符串或一个整数。
 */
class MessageParam final : public Serializable {
    // binary representation of the parameter   参数的二进制表示
    std::string value;
    enum class Type {
        INT,
        STRING
    } type;

public:
    MessageParam(): type(Type::STRING) {};
    explicit MessageParam(const int&);
    explicit MessageParam(const std::string&);
    [[nodiscard]] std::string getValueStr() const;
    [[nodiscard]] int getValueInt() const;
    ~MessageParam() override;

    [[nodiscard]] std::string serialize() const override;
    size_t deserialize(const std::string& data) override;
};

/**
 * Message class for RPC.
 * It contains a list of parameters to be sent over the network.
 * 
 * 用于远程调用的消息类
 * 它包含一个待调用的函数名和一组参数
 */
class Message final : public Serializable {
    std::string functionName;
    std::vector<MessageParam> content;

public:
    Message()=default;
    ~Message() override =default;

    // Get all message parameters waiting for serialization
    [[nodiscard]] const std::vector<MessageParam>& getContent() const;

    [[nodiscard]] std::string serialize() const override;
    size_t deserialize(const std::string &) override;

    // Append a message parameter at the end
    void appendMessageParam(const MessageParam& param);
};

#endif