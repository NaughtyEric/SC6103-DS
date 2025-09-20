#ifndef CLIENT_MANAGER_HPP
#define CLIENT_MANAGER_HPP
#include <string>

class Address
{
    std::string IP;
    int port;
public:
    Address(): IP("127.0.0.1"), port(8080) {}
    Address(const std::string& ip, const int &port): IP(ip), port(port) {}
    [[nodiscard]] std::string getIP() const { return IP; }
    [[nodiscard]] int getPort() const { return port; }
};


class Client
{
    Address address;
    unsigned clientID;  // Unique identifier for the client
public:
    Client(const Address& addr, const unsigned &id): address(addr), clientID(id) {}
    [[nodiscard]] Address getAddress() const { return address; }
    [[nodiscard]] unsigned getClientID() const { return clientID; }
};


class ClientManager {
private:
    
public:
    ClientManager();
    ~ClientManager();


};

#endif