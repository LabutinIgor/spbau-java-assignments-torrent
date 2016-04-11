package ru.spbau.mit;

import java.util.Arrays;

public class ClientInfo {
    public byte[] ip;
    public short port;
    public long lastUpdateTime;

    public Integer getHash() {
        return Arrays.hashCode(ip) + port;
    }
}
