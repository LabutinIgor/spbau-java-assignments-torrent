package ru.spbau.mit;

import java.util.Set;

public class FileInfo {
    public int id;
    public String name;
    public long size;
    public Set<ClientInfo> clients;
    public boolean[] isDownloadedPart;
}
