package ru.spbau.mit;

import java.util.Arrays;

public class ClientInfo {
    private byte[] ip;
    private short port;
    private long lastUpdateTime;

    public byte[] getIp() {
        return ip;
    }

    public short getPort() {
        return port;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setIp(byte[] ip) {
        this.ip = ip;
    }

    public void setPort(short port) {
        this.port = port;
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
