#include "include/message.hpp"
#include <iostream>

const size_t INDICATOR_LENGTH = sizeof(unsigned char);

/**
 * Convert int to HEX small-endian
 * 
 * 将整数值转换为小端格式的十六进制字符串
 */
MessageParam::MessageParam(int intValue) {
    value = "";
    for (int i = 0; i < sizeof(int); i++) {
        char byte = (intValue >> (i * 8)) & 0xFF;
        value += byte;
    }
    type = Type::INT;
}

/**
 * Save string directly
 *
 * 将字符串值直接保存
 */
MessageParam::MessageParam(std::string strValue) {
    value = strValue;
    type = Type::STRING;
}

/**
 * Get the string value of the parameter.
 * 
 * 以字符串的形式获取参数的值。
 * 
 * @exception std::runtime_error if the type is not a string
 */
std::string MessageParam::getValueStr() const {
#ifndef DEBUG
// 在调试模式下不检查类型，允许输出为字符串以方便检查
    if (type != Type::STRING) {
        throw std::exception("Parameter is not a string");
    }
#endif
    return value;
}

/**
 * Get the integer value of the parameter.
 * 
 * 以整数的形式获取参数的值。
 * 
 * @exception std::runtime_error if the type is not an integer
 */
int MessageParam::getValueInt() const {
    if (type != Type::INT) {
        throw std::exception("Parameter is not an integer");
    }
    int intValue = 0;
    for (int i = 0; i < sizeof(int); i++) {
        intValue |= (static_cast<int>(value[i]) << (i * 8));
    }
    return intValue;
}

MessageParam::~MessageParam() {}

/**
 * Serialize the parameter.
 * If the parameter is an integer, it is serialized as a 4-byte little-endian value with \xff as indicator.
 * If the parameter is a string, it is serialized as a length-prefixed string, and the first byte indicates the length.
 * 
 * 序列化该参数。
 * 如果参数是整数，则将其序列化为4字节小端值，并以\xff作为标志。
 * 如果参数是字符串，则将其序列化为长度前缀字符串，第一个字节表示长度。长度不超过254。
 * 
 * @exception std::runtime_error if the string length exceeds 254 characters
 */
std::string MessageParam::serialize() const {
    if (type == Type::INT) { // \xff as indicator
        return '\xff'+value;
    } else {
        size_t len = value.length();
        if (len >= 0xff) {
            throw std::exception("Length of string to be serialized exceeds 254 characters");
        }
        return char(len) + value;
    }
}

/**
 * Deserialize the parameter from a string. If the string is longer than requirement, only the required part is consumed.
 *
 * 从字符串中反序列化参数。如果字符串长于所需部分，仅使用所需的部分。
 *
 * @return the length of the consumed part
 *
 * @exception std::runtime_error if the data is insufficient
 */
size_t MessageParam::deserialize(std::string data) {
    if (data.empty()) {
        throw std::exception("Cannot deserialize from empty string");
    }
    unsigned char indicator = data[0];
    if (indicator == 0xff) { // integer
        size_t designatedLength = INDICATOR_LENGTH + sizeof(int);
        if (data.length() < designatedLength) {
            std::string errorInfo = "Insufficient data for integer deserialization";
            errorInfo += " (expected " + std::to_string(designatedLength) + ", got " + std::to_string(data.length()) + ")";
            throw std::exception(errorInfo.c_str());
        }
        value = data.substr(INDICATOR_LENGTH, sizeof(int));
        type = Type::INT;
        return INDICATOR_LENGTH + sizeof(int);
    } else { // string
        size_t len = static_cast<size_t>(indicator);
        if (data.length() < INDICATOR_LENGTH + len) {
            std::string errorInfo = "Insufficient data for string deserialization";
            errorInfo += " (expected " + std::to_string(INDICATOR_LENGTH + len) + ", got " + std::to_string(data.length()) + ")";
            throw std::exception(errorInfo.c_str());
        }
        value = data.substr(INDICATOR_LENGTH, len);
        type = Type::STRING;
        return INDICATOR_LENGTH + len;
    }
}

/**
 * @return the const reference to the vector of message parameters.
 */
const std::vector<MessageParam>& Message::getContent() const {
    return content;
}

/**
 * Serialize the message.
 * 
 * 序列化该消息。
 * 
 * @return the serialized message as a string.
 */
std::string Message::serialize() const {
    std::string result;
    for (const auto& param : content) {
        result += param.serialize();
    }
    MessageParam fnParam(functionName);
    result += fnParam.serialize();
    return result;
}

/**
 * Deserialize the message from a string.
 * 
 * 从字符串中反序列化该消息。
 * 
 * @exception std::runtime_error if the data is invalid or insufficient
 */
size_t Message::deserialize(std::string data) {
    content.clear();
    size_t pos = 0;
    while (pos < data.length()) {
        try {
            MessageParam param;
            size_t consumed = param.deserialize(data.substr(pos));
            if (consumed == 0) {
                break;
            }
            if (pos == 0) { // first parameter should be string as function name
                functionName = param.getValueStr();
            } else {
                content.push_back(param);
            }
            pos += consumed;
        } catch (const std::exception& e) {
            // Handle deserialization error
            std::cerr << "Error deserializing message parameter: " << e.what() << std::endl;
            break;
        }
    }
    return pos;
}