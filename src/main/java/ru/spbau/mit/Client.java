package ru.spbau.mit;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Client {
    private static final int PORT = 8081;
    private static final long UPDATE_TIME = 60000;
    private static final long SAVING_STATE_PERIOD = 60000;
    private static final byte LIST = 1;
    private static final byte UPLOAD = 2;
    private static final byte SOURCES = 3;
    private static final byte UPDATE = 4;
    private static final byte STAT = 1;
    private static final byte GET = 2;
    private static final int ARGS_LENGTH_LIST = 2;
    private static final int ARGS_LENGTH_GET = 3;
    private static final int ARGS_LENGTH_NEWFILE = 3;
    private static final int ARGS_LENGTH_RUN = 2;
    private static final int PART_SIZE = 4096;
    private static final int IP_LENGTH = 4;
    private static final int ARG_ID = 2;
    private static final int ARG_PATH = 2;
    private static final int ARG_HOST = 1;
    private static final int MIN_ARGS_CNT = 2;
    private static final String CONFIG_FILE = "config.txt";

    private static String host;
    private static int port;
    private static Map<Integer, FileInfo> filesById;

    private Client() {
    }

    public static void main(String[] args) throws IOException {
        loadState();

        if (args.length < MIN_ARGS_CNT) {
            printUsage();
            return;
        }
        host = args[ARG_HOST];
        switch (args[0]) {
            case "list":
                if (args.length != ARGS_LENGTH_LIST) {
                    printUsage();
                    return;
                }
                Map<Integer, FileInfo> filesFromServer = list();
                for (FileInfo file : filesFromServer.values()) {
                    System.out.println("id: " + file.id + " name: " + file.name + " size: " + file.size);
                }
                break;
            case "get":
                if (args.length != ARGS_LENGTH_GET) {
                    printUsage();
                    return;
                }
                get(args[ARG_ID]);
                break;
            case "newfile":
                if (args.length != ARGS_LENGTH_NEWFILE) {
                    printUsage();
                    return;
                }
                newfile(args[ARG_PATH]);
                break;
            case "run":
                if (args.length != ARGS_LENGTH_RUN) {
                    printUsage();
                    return;
                }
                run();
                break;
            default:
                printUsage();
                break;
        }
        saveState();
    }

    private static void loadState() throws IOException {
        File config = new File(CONFIG_FILE);
        if (config.exists()) {
            final DataInputStream in = new DataInputStream(new FileInputStream(config));

            int cnt = in.readInt();
            for (int i = 0; i < cnt; i++) {
                FileInfo file = new FileInfo();
                file.id = in.readInt();
                file.startedDownloading = in.readBoolean();
                if (file.startedDownloading) {
                    file.name = in.readUTF();
                    file.size = in.readLong();
                    byte[] parts = new byte[file.getPartsCnt()];
                    if (in.read(parts) != file.getPartsCnt()) {
                        throw new IOException("Error while loading config");
                    }
                    for (int part = 0; part < file.getPartsCnt(); part++) {
                        file.isDownloadedPart[part] = parts[part] != 0;
                    }
                }
                filesById.put(file.id, file);
            }

            in.close();
        } else {
            boolean configCreated = config.createNewFile();
            if (!configCreated) {
                throw new IOException("Error while creating config file");
            }
            filesById = new HashMap<>();
            saveState();
        }
    }

    private static void saveState() throws IOException {
        final DataOutputStream out = new DataOutputStream(new FileOutputStream(CONFIG_FILE));

        out.writeInt(filesById.size());
        for (FileInfo file : filesById.values()) {
            out.writeInt(file.id);
            out.writeBoolean(file.startedDownloading);
            if (file.startedDownloading) {
                out.writeUTF(file.name);
                out.writeLong(file.size);
                for (int i = 0; i < file.getPartsCnt(); i++) {
                    out.write(file.isDownloadedPart[i] ? 1 : 0);
                }
            }
        }
        out.close();
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("list <tracker-address>");
        System.out.println("get <tracker-address> <file-id>");
        System.out.println("newfile <tracker-address> <path>");
        System.out.println("run <tracker-address>");
    }

    private static Map<Integer, FileInfo> list() throws IOException {
        Socket socket = new Socket(host, PORT);
        socket.getOutputStream().write(LIST);

        final DataInputStream inputStream = new DataInputStream(socket.getInputStream());

        Map<Integer, FileInfo> files = new HashMap<>();
        int cnt = inputStream.readInt();

        for (int i = 0; i < cnt; i++) {
            FileInfo file = new FileInfo();
            file.id = inputStream.readInt();
            file.name = inputStream.readUTF();
            file.size = inputStream.readLong();

            files.put(file.id, file);
        }
        return files;
    }

    private static void get(String id) {
        FileInfo file = new FileInfo();
        file.id = Integer.parseInt(id);
        file.startedDownloading = false;
        filesById.put(file.id, file);
    }

    private static void newfile(String path) throws IOException {
        Socket socket = new Socket(host, PORT);
        final DataInputStream inputStream = new DataInputStream(socket.getInputStream());

        FileInfo file = new FileInfo();
        file.file = new RandomAccessFile(path, "r");
        file.name = path;
        file.size = file.file.length();
        file.startedDownloading = true;
        file.cntDownloadedParts = file.getPartsCnt();

        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
        outputStream.writeByte(UPLOAD);
        outputStream.writeUTF(file.name);
        outputStream.writeLong(file.size);

        file.id = inputStream.readInt();
        filesById.put(file.id, file);

        socket.close();
    }

    private static void run() throws IOException {
        Map<Integer, FileInfo> filesFromServer = list();

        filesById.values().stream().filter(file -> filesFromServer.keySet().contains(file.id)
                && !file.startedDownloading).forEach(file -> {
            FileInfo fileFromServer = filesFromServer.get(file.id);
            file.startedDownloading = true;
            file.name = fileFromServer.name;
            file.size = fileFromServer.size;
            try {
                file.file = new RandomAccessFile(file.name, "rw");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            file.isDownloadedPart = new boolean[file.getPartsCnt()];
        });

        //Thread to update
        new Thread(() -> {
            while (true) {
                try {
                    sendUpdate();
                    Thread.sleep(UPDATE_TIME);
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        //Thread to download
        new Thread(() -> {
            while (true) {
                try {
                    for (FileInfo file : filesById.values()) {
                        if (file.startedDownloading && file.cntDownloadedParts != file.getPartsCnt()) {
                            List<ClientInfo> clients = sendSources(file.id);
                            for (ClientInfo client : clients) {
                                List<Integer> parts = sendStat(client.ip, client.port, file.id);
                                for (Integer part : parts) {
                                    if (!file.isDownloadedPart[part]) {
                                        file.isDownloadedPart[part] = true;
                                        file.cntDownloadedParts++;
                                        byte[] buffer = sendGet(client.ip, client.port, file.id, part);
                                        file.file.seek(part * (long) PART_SIZE);
                                        file.file.write(buffer);
                                    }
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        //Thread to seed
        new Thread(() -> {
            try {
                ServerSocket serverSocket = new ServerSocket(0);
                port = serverSocket.getLocalPort();
                while (true) {
                    Socket socket = serverSocket.accept();
                    if (socket == null) {
                        break;
                    }
                    doQuery(socket);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();


        while (true) {
            saveState();
            try {
                Thread.sleep(SAVING_STATE_PERIOD);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean sendUpdate() throws IOException {
        Socket socket = new Socket(host, PORT);
        final DataInputStream inputStream = new DataInputStream(socket.getInputStream());
        final DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

        outputStream.writeByte(UPDATE);
        outputStream.writeShort(port);
        outputStream.writeInt(filesById.size());
        for (Integer id : filesById.keySet()) {
            outputStream.writeInt(id);
        }

        boolean result = inputStream.readBoolean();

        socket.close();
        return result;
    }

    private static List<ClientInfo> sendSources(int id) throws IOException {
        Socket socket = new Socket(host, PORT);
        final DataInputStream inputStream = new DataInputStream(socket.getInputStream());
        final DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

        outputStream.writeByte(SOURCES);
        outputStream.writeInt(id);

        List<ClientInfo> clients = new ArrayList<>();
        int cnt = inputStream.readInt();
        for (int i = 0; i < cnt; i++) {
            ClientInfo client = new ClientInfo();
            client.ip = new byte[IP_LENGTH];
            if (inputStream.read(client.ip) != IP_LENGTH) {
                throw new IOException("Error in sources");
            }
            client.port = inputStream.readByte();

            clients.add(client);
        }

        socket.close();
        return clients;
    }

    private static List<Integer> sendStat(byte[] clientIp, int clientPort, int id) throws IOException {
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

    private static byte[] sendGet(byte[] clientIp, int clientPort, int id, int part) throws IOException {
        Socket socket = new Socket(InetAddress.getByAddress(clientIp), clientPort);
        final DataInputStream inputStream = new DataInputStream(socket.getInputStream());
        final DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

        outputStream.writeByte(GET);
        outputStream.writeInt(id);
        outputStream.writeInt(part);

        byte[] buffer = new byte[PART_SIZE];

        if (inputStream.read(buffer) != PART_SIZE) {
            throw new IOException("Error in get query");
        }

        socket.close();
        return buffer;
    }

    private static void doQuery(Socket socket) {
        try (DataInputStream inputStream = new DataInputStream(socket.getInputStream());
             DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream())) {
            while (!socket.isClosed()) {
                byte request = inputStream.readByte();
                switch (request) {
                    case STAT:
                        doStat(inputStream, outputStream);
                        break;
                    case GET:
                        doGet(inputStream, outputStream);
                        break;
                    default:
                        System.err.println("Incorrect query");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void doStat(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        int id = inputStream.readInt();
        FileInfo file = filesById.get(id);
        List<Integer> parts = new ArrayList<>();
        for (int i = 0; i < file.getPartsCnt(); i++) {
            if (file.isDownloadedPart[i]) {
                parts.add(i);
            }
        }
        outputStream.writeInt(parts.size());
        for (Integer part : parts) {
            outputStream.writeInt(part);
        }
    }

    private static void doGet(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        int id = inputStream.readInt();
        int part = inputStream.readInt();

        byte[] buffer = new byte[PART_SIZE];

        FileInfo file = filesById.get(id);
        file.file.seek(part * (long) PART_SIZE);
        file.file.read(buffer);

        outputStream.write(buffer);
    }
}
