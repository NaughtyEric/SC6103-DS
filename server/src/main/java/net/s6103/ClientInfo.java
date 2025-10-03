package net.s6103;

import java.net.InetAddress;

public record ClientInfo(InetAddress ip, int port) { }
