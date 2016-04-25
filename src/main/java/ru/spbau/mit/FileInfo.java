package ru.spbau.mit;

import java.io.RandomAccessFile;
import java.util.Set;

public class FileInfo {
    private static final int PART_SIZE = 4096;

    private final int id;
    private final String name;
    private final long size;
    private Set<ClientInfo> seeds;
    private RandomAccessFile file;
    private int cntDownloadedParts;
    private boolean[] isDownloadedPart;

    public FileInfo(int id, String name, long size) {
        this.id = id;
        this.name = name;
        this.size = size;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public Set<ClientInfo> getSeeds() {
        return seeds;
    }

    public RandomAccessFile getFile() {
        return file;
    }

    public int getCntDownloadedParts() {
        return cntDownloadedParts;
    }

    public boolean getIsDownloadedPart(int part) {
        return isDownloadedPart[part];
    }

    public void setSeeds(Set<ClientInfo> clients) {
        this.seeds = clients;
    }

    public void setFile(RandomAccessFile file) {
        this.file = file;
    }

    public void setCntDownloadedParts(int cntDownloadedParts) {
        this.cntDownloadedParts = cntDownloadedParts;
    }

    public void setIsDownloadedPart(int part, boolean isDownloadedPart) {
        this.isDownloadedPart[part] = isDownloadedPart;
    }

    public void setIsDownloadedParts(boolean[] isDownloadedPart) {
        this.isDownloadedPart = isDownloadedPart;
    }

    public int getPartsCnt() {
        return (int) (size / PART_SIZE);
    }
}
