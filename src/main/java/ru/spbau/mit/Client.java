package ru.spbau.mit;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Client {
    private static final int PORT = 8081;
    private static final long UPDATE_TIME = 60000;
    private static final byte LIST = 1;
    private static final byte UPLOAD = 2;
    private static final byte SOURCES = 3;
    private static final byte UPDATE = 4;
    private static final byte STAT = 1;
    private static final byte GET = 2;
    private static final int PART_SIZE = 4096;

    private String host;
    private int port;
    private Map<Integer, FileInfo> filesById;
    private String configFile;

    public Client(String configFile) {
        this.configFile = configFile;
    }

    public static void main(String[] args) throws IOException {
        new Client("config.txt").start(args);
    }

    public void start(String[] args) throws IOException {
        loadState();

        if (args.length < 2) {
            printUsageAndExit();
        }
        host = args[1];
        switch (args[0]) {
            case "list":
                if (args.length != 2) {
                    printUsageAndExit();
                }
                Map<Integer, FileInfo> filesFromServer = sendListQuery();
                System.out.println(filesFromServer.size());
                for (FileInfo file : filesFromServer.values()) {
                    System.out.println("id: " + file.getId() + " name: " + file.getName() + " size: "
                            + file.getSize());
                }
                break;
            case "get":
                if (args.length != 3) {
                    printUsageAndExit();
                }
                get(args[2]);
                break;
            case "newfile":
                if (args.length != 3) {
                    printUsageAndExit();
                }
                uploadFile(args[2]);
                break;
            case "run":
                if (args.length != 2) {
                    printUsageAndExit();
                }
                run();
                break;
            default:
                printUsageAndExit();
        }
        saveState();
    }

    private void loadState() throws IOException {
        File config = new File(configFile);
        filesById = new HashMap<>();
        if (config.exists()) {
            final DataInputStream in = new DataInputStream(new FileInputStream(config));

            int cnt = in.readInt();
            for (int i = 0; i < cnt; i++) {
                FileInfo file = null;
                int id = in.readInt();
                if (in.readBoolean()) {
                    String name = in.readUTF();
                    String path = in.readUTF();
                    long size = in.readLong();
                    file = new FileInfo(id, name, path, size);
                    file.setCntDownloadedParts(in.readInt());

                    byte[] parts = new byte[file.getPartsCnt()];
                    file.setIsDownloadedParts(new boolean[file.getPartsCnt()]);
                    if (in.read(parts) != file.getPartsCnt()) {
                        throw new IOException("Error while loading config");
                    }
                    for (int part = 0; part < file.getPartsCnt(); part++) {
                        file.setIsDownloadedPart(part, parts[part] != 0);
                    }
                }
                filesById.put(id, file);
            }

            in.close();
        } else {
            boolean configCreated = config.createNewFile();
            if (!configCreated) {
                throw new IOException("Error while creating config file");
            }
            saveState();
        }
    }

    private void saveState() throws IOException {
        final DataOutputStream out = new DataOutputStream(new FileOutputStream(configFile));

        out.writeInt(filesById.size());
        for (Map.Entry<Integer, FileInfo> entry : filesById.entrySet()) {
            out.writeInt(entry.getKey());
            FileInfo file = entry.getValue();
            out.writeBoolean(file != null);
            if (file != null) {
                out.writeUTF(file.getName());
                out.writeUTF(file.getPath());
                out.writeLong(file.getSize());
                out.writeInt(file.getCntDownloadedParts());
                for (int i = 0; i < file.getPartsCnt(); i++) {
                    out.write(file.getIsDownloadedPart(i) ? 1 : 0);
                }
            }
        }
        out.close();
    }

    private void printUsageAndExit() {
        System.out.println("Usage:");
        System.out.println("list <tracker-address>");
        System.out.println("get <tracker-address> <file-id>");
        System.out.println("newfile <tracker-address> <path>");
        System.out.println("run <tracker-address>");
        System.exit(0);
    }

    private Map<Integer, FileInfo> sendListQuery() throws IOException {
        Socket socket = new Socket(host, PORT);
        socket.getOutputStream().write(LIST);

        final DataInputStream inputStream = new DataInputStream(socket.getInputStream());

        Map<Integer, FileInfo> files = new HashMap<>();
        int cnt = inputStream.readInt();

        for (int i = 0; i < cnt; i++) {
            int id = inputStream.readInt();
            String name = inputStream.readUTF();
            long size = inputStream.readLong();
            FileInfo file = new FileInfo(id, name, "downloads/" + name, size);
            file.setIsDownloadedParts(new boolean[file.getPartsCnt()]);
            files.put(file.getId(), file);
        }
        socket.close();
        return files;
    }

    private void get(String id) {
        filesById.put(Integer.parseInt(id), null);
    }

    private void uploadFile(String path) throws IOException {
        Socket socket = new Socket(host, PORT);
        final DataInputStream inputStream = new DataInputStream(socket.getInputStream());

        RandomAccessFile uploadingFile = new RandomAccessFile(path, "r");
        final long size = uploadingFile.length();
        uploadingFile.close();

        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
        outputStream.writeByte(UPLOAD);
        outputStream.writeUTF(path);
        outputStream.writeLong(size);

        int id = inputStream.readInt();
        FileInfo file = new FileInfo(id, path, path, size);

        file.setIsDownloadedParts(new boolean[file.getPartsCnt()]);
        for (int i = 0; i < file.getPartsCnt(); i++) {
            file.setIsDownloadedPart(i, true);
        }
        file.setCntDownloadedParts(file.getPartsCnt());

        filesById.put(id, file);

        socket.close();
    }

    private void run() throws IOException {
        ServerSocket serverSocket = new ServerSocket(0);
        port = serverSocket.getLocalPort();
        //System.err.println("new port: " + port);

        Map<Integer, FileInfo> filesFromServer = sendListQuery();

        for (Map.Entry<Integer, FileInfo> entry : filesById.entrySet()) {
            if (entry.getValue() == null && filesFromServer.keySet().contains(entry.getKey())) {
                FileInfo fileFromServer = filesFromServer.get(entry.getKey());
                FileInfo file = new FileInfo(fileFromServer.getId(), fileFromServer.getName(),
                        "downloads/" + fileFromServer.getName(), fileFromServer.getSize());
                file.setIsDownloadedParts(new boolean[file.getPartsCnt()]);
                entry.setValue(file);
            }
        }

        saveState();

        TimerTask updateTask = new TimerTask() {
            @Override
            public void run() {
                //System.err.println("UPDATE");
                sendUpdateQuery();
            }
        };
        Timer timerToUpdate = new Timer();
        timerToUpdate.schedule(updateTask, 0, UPDATE_TIME);

        //Thread to download
        new Thread(() -> {
            while (true) {
                for (FileInfo file : filesById.values()) {
                    if (file != null && file.getCntDownloadedParts() != file.getPartsCnt()) {
                        //System.err.println("Download " + file.getName());
                        downloadFile(file);
                    }
                }
            }
        }).start();

        while (true) {
            Socket socket = serverSocket.accept();
            if (socket == null) {
                break;
            }
            processQuery(socket);
            socket.close();
        }
    }

    private void downloadFile(FileInfo file) {
        try {
            List<ClientInfo> clients = sendSourcesQuery(file.getId());
            for (ClientInfo client : clients) {
                List<Integer> parts = sendStatQuery(client.getIp(), client.getPort(),
                        file.getId());
                //System.err.println("Get parts from client with port " + client.getPort());
                for (Integer part : parts) {
                    if (!file.getIsDownloadedPart(part)) {
                        //System.err.println("Download part " + part);
                        file.setIsDownloadedPart(part, true);
                        file.setCntDownloadedParts(file.getCntDownloadedParts() + 1);
                        byte[] buffer = sendGetQuery(client.getIp(), client.getPort(),
                                file.getId(), part, file.getPartSize(part));
                        RandomAccessFile randomAccessFile = new RandomAccessFile(file.getPath(), "rw");
                        randomAccessFile.seek(part * (long) PART_SIZE);
                        randomAccessFile.write(buffer);
                        randomAccessFile.close();
                        saveState();
                    }
                }
            }
            //System.err.println("File " + file.getName() + " downloaded!");
        } catch (IOException e) {
            System.err.println("Error in downloading file " + file.getName());
            e.printStackTrace();
        }
    }

    private boolean sendUpdateQuery() {
        try {
            Socket socket = new Socket(host, PORT);
            final DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            final DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

            //System.err.println("Send update from " + port);

            outputStream.writeByte(UPDATE);
            outputStream.writeInt(port);
            outputStream.writeInt(filesById.size());
            for (Integer id : filesById.keySet()) {
                outputStream.writeInt(id);
            }

            boolean result = inputStream.readBoolean();

            socket.close();
            return result;
        } catch (IOException e) {
            System.err.println("Error in sending update");
            return false;
        }
    }

    private List<ClientInfo> sendSourcesQuery(int id) throws IOException {
        Socket socket = new Socket(host, PORT);
        //System.err.println("Send sources to file " + id);
        final DataInputStream inputStream = new DataInputStream(socket.getInputStream());
        final DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

        outputStream.writeByte(SOURCES);
        outputStream.writeInt(id);

        List<ClientInfo> clients = new ArrayList<>();
        int cnt = inputStream.readInt();
        for (int i = 0; i < cnt; i++) {
            byte[] ip = new byte[4];
            if (inputStream.read(ip) != 4) {
                throw new IOException("Error in sources");
            }
            int port = inputStream.readInt();
            ClientInfo client = new ClientInfo(ip, port);

            clients.add(client);
        }
        //System.err.println("Sources result cnt: " + cnt);

        socket.close();
        return clients;
    }

    private List<Integer> sendStatQuery(byte[] clientIp, int clientPort, int id) throws IOException {
        Socket socket = new Socket(InetAddress.getByAddress(clientIp), clientPort);
        final DataInputStream inputStream = new DataInputStream(socket.getInputStream());
        final DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

        outputStream.writeByte(STAT);
        outputStream.writeInt(id);

        List<Integer> parts = new ArrayList<>();
        int cnt = inputStream.readInt();
        for (int i = 0; i < cnt; i++) {
            parts.add(inputStream.readInt());
        }

        socket.close();
        return parts;
    }

    private byte[] sendGetQuery(byte[] clientIp, int clientPort, int id, int part, int partSize)
            throws IOException {
        Socket socket = new Socket(InetAddress.getByAddress(clientIp), clientPort);
        final DataInputStream inputStream = new DataInputStream(socket.getInputStream());
        final DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

        outputStream.writeByte(GET);
        outputStream.writeInt(id);
        outputStream.writeInt(part);

        byte[] buffer = new byte[partSize];

        if (inputStream.read(buffer) != partSize) {
            throw new IOException("Error in get query");
        }

        socket.close();
        return buffer;
    }

    private void processQuery(Socket socket) throws IOException {
        try (DataInputStream inputStream = new DataInputStream(socket.getInputStream());
             DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream())) {
            byte request = inputStream.readByte();
            switch (request) {
                case STAT:
                    processStatQuery(inputStream, outputStream);
                    break;
                case GET:
                    processGetQuery(inputStream, outputStream);
                    break;
                default:
                    System.err.println("Incorrect query");
            }
        }
    }

    private void processStatQuery(DataInputStream inputStream, DataOutputStream outputStream)
            throws IOException {
        int id = inputStream.readInt();
        FileInfo file = filesById.get(id);
        List<Integer> parts = new ArrayList<>();
        for (int i = 0; i < file.getPartsCnt(); i++) {
            if (file.getIsDownloadedPart(i)) {
                parts.add(i);
            }
        }
        outputStream.writeInt(parts.size());
        for (Integer part : parts) {
            outputStream.writeInt(part);
        }
    }

    private void processGetQuery(DataInputStream inputStream, DataOutputStream outputStream)
            throws IOException {
        int id = inputStream.readInt();
        int part = inputStream.readInt();
        FileInfo file = filesById.get(id);

        RandomAccessFile randomAccessFile = new RandomAccessFile(file.getPath(), "r");
        randomAccessFile.seek(part * (long) PART_SIZE);
        byte[] buffer = new byte[file.getPartSize(part)];
        randomAccessFile.read(buffer);
        randomAccessFile.close();

        outputStream.write(buffer);
    }
}
