package ru.spbau.mit;

import java.util.Set;

public class FileInfo {
    private static final int PART_SIZE = 1024 * 1024;

    private final int id;
    private final String name;
    private String path;
    private final long size;
    private Set<ClientInfo> seeds;
    private int cntDownloadedParts;
    private boolean[] isDownloadedPart;

    public FileInfo(int id, String name, long size) {
        this.id = id;
        this.name = name;
        this.size = size;
    }

    public FileInfo(int id, String name, String path, long size) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.size = size;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

    public Set<ClientInfo> getSeeds() {
        return seeds;
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
        return (int) (size / PART_SIZE + (size % PART_SIZE == 0 ? 0 : 1));
    }

    public int getPartSize(int part) {
        return part == getPartsCnt() - 1 ? (int) (size % PART_SIZE) : PART_SIZE;
    }
}
