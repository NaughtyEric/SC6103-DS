/**
 * Abstract class for serializable objects.
 */
#ifndef SERIALIZABLE_HPP
#define SERIALIZABLE_HPP
#include <string>

class Serializable {
public:
    virtual ~Serializable() = default;

private:
    /**
     * Serializes the object to a string.
     * @return The serialized string representation of the object.
     */
    [[nodiscard]] virtual std::string serialize() const = 0;

    /**
     * Deserializes the object from a string.
     * @param data The serialized string representation of the object.
     */
    virtual size_t deserialize(const std::string &data) = 0;
};

#endif