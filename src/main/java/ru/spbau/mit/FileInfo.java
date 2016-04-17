package ru.spbau.mit;

import java.io.RandomAccessFile;
import java.util.Set;

public class FileInfo {
    private static final int PART_SIZE = 4096;

    public int id;
    public String name;
    public long size;
    public Set<ClientInfo> clients;
    RandomAccessFile file;
    public boolean startedDownloading;
    public boolean[] isDownloadedPart;

    public int getPartsCnt() {
        return (int) (size / PART_SIZE);
    }
}
