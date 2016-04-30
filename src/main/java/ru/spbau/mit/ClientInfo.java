package ru.spbau.mit;

import java.util.Arrays;

public class ClientInfo {
    private final byte[] ip;
    private final int port;
    private long lastUpdateTime;

    public ClientInfo(byte[] ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public byte[] getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    @Override
    public boolean equals(Object object) {
        return object != null && object.getClass() == getClass()
                && ((ClientInfo) object).port == port && Arrays.equals(((ClientInfo) object).ip, ip);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ip) * 31 + port;
    }
}
