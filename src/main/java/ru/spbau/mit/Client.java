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

    public static void main(String[] args) throws IOException {
        new Client().start(args);
    }

    public void start(String[] args) throws IOException {
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
                    System.out.println("id: " + file.getId() + " name: " + file.getName() + " size: " +
                            file.getSize());
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

    private void loadState() throws IOException {
        File config = new File(CONFIG_FILE);
        if (config.exists()) {
            final DataInputStream in = new DataInputStream(new FileInputStream(config));

            int cnt = in.readInt();
            for (int i = 0; i < cnt; i++) {
                FileInfo file = new FileInfo();
                file.setId(in.readInt());
                file.setStartedDownloading(in.readBoolean());
                if (file.getStartedDownloading()) {
                    file.setName(in.readUTF());
                    file.setSize(in.readLong());
                    byte[] parts = new byte[file.getPartsCnt()];
                    if (in.read(parts) != file.getPartsCnt()) {
                        throw new IOException("Error while loading config");
                    }
                    for (int part = 0; part < file.getPartsCnt(); part++) {
                        file.setIsDownloadedPart(part, parts[part] != 0);
                    }
                }
                filesById.put(file.getId(), file);
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

    private void saveState() throws IOException {
        final DataOutputStream out = new DataOutputStream(new FileOutputStream(CONFIG_FILE));

        out.writeInt(filesById.size());
        for (FileInfo file : filesById.values()) {
            out.writeInt(file.getId());
            out.writeBoolean(file.getStartedDownloading());
            if (file.getStartedDownloading()) {
                out.writeUTF(file.getName());
                out.writeLong(file.getSize());
                for (int i = 0; i < file.getPartsCnt(); i++) {
                    out.write(file.getIsDownloadedPart(i) ? 1 : 0);
                }
            }
        }
        out.close();
    }

    private void printUsage() {
        System.out.println("Usage:");
        System.out.println("list <tracker-address>");
        System.out.println("get <tracker-address> <file-id>");
        System.out.println("newfile <tracker-address> <path>");
        System.out.println("run <tracker-address>");
    }

    private Map<Integer, FileInfo> list() throws IOException {
        Socket socket = new Socket(host, PORT);
        socket.getOutputStream().write(LIST);

        final DataInputStream inputStream = new DataInputStream(socket.getInputStream());

        Map<Integer, FileInfo> files = new HashMap<>();
        int cnt = inputStream.readInt();

        for (int i = 0; i < cnt; i++) {
            FileInfo file = new FileInfo();
            file.setId(inputStream.readInt());
            file.setName(inputStream.readUTF());
            file.setSize(inputStream.readLong());

            files.put(file.getId(), file);
        }
        return files;
    }

    private void get(String id) {
        FileInfo file = new FileInfo();
        file.setId(Integer.parseInt(id));
        file.setStartedDownloading(false);
        filesById.put(file.getId(), file);
    }

    private void newfile(String path) throws IOException {
        Socket socket = new Socket(host, PORT);
        final DataInputStream inputStream = new DataInputStream(socket.getInputStream());

        FileInfo file = new FileInfo();
        file.setFile(new RandomAccessFile(path, "r"));
        file.setName(path);
        file.setSize(file.getFile().length());
        file.setStartedDownloading(true);
        file.setCntDownloadedParts(file.getPartsCnt());

        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
        outputStream.writeByte(UPLOAD);
        outputStream.writeUTF(file.getName());
        outputStream.writeLong(file.getSize());

        file.setId(inputStream.readInt());
        filesById.put(file.getId(), file);

        socket.close();
    }

    private void run() throws IOException {
        Map<Integer, FileInfo> filesFromServer = list();

        filesById.values().stream().filter(file -> filesFromServer.keySet().contains(file.getId())
                && !file.getStartedDownloading()).forEach(file -> {
            FileInfo fileFromServer = filesFromServer.get(file.getId());
            file.setStartedDownloading(true);
            file.setName(fileFromServer.getName());
            file.setSize(fileFromServer.getSize());
            try {
                file.setFile(new RandomAccessFile(file.getName(), "rw"));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            file.setIsDownloadedParts(new boolean[file.getPartsCnt()]);
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
                        if (file.getStartedDownloading() && file.getCntDownloadedParts() != file.getPartsCnt()) {
                            List<ClientInfo> clients = sendSources(file.getId());
                            for (ClientInfo client : clients) {
                                List<Integer> parts = sendStat(client.getIp(), client.getPort(), file.getId());
                                for (Integer part : parts) {
                                    if (!file.getIsDownloadedPart(part)) {
                                        file.setIsDownloadedPart(part, true);
                                        file.setCntDownloadedParts(file.getCntDownloadedParts() + 1);
                                        byte[] buffer = sendGet(client.getIp(), client.getPort(),
                                                file.getId(), part);
                                        file.getFile().seek(part * (long) PART_SIZE);
                                        file.getFile().write(buffer);
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

    private boolean sendUpdate() throws IOException {
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

    private List<ClientInfo> sendSources(int id) throws IOException {
        Socket socket = new Socket(host, PORT);
        final DataInputStream inputStream = new DataInputStream(socket.getInputStream());
        final DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

        outputStream.writeByte(SOURCES);
        outputStream.writeInt(id);

        List<ClientInfo> clients = new ArrayList<>();
        int cnt = inputStream.readInt();
        for (int i = 0; i < cnt; i++) {
            ClientInfo client = new ClientInfo();
            client.setIp(new byte[IP_LENGTH]);
            if (inputStream.read(client.getIp()) != IP_LENGTH) {
                throw new IOException("Error in sources");
            }
            client.setPort(inputStream.readByte());

            clients.add(client);
        }

        socket.close();
        return clients;
    }

    private List<Integer> sendStat(byte[] clientIp, int clientPort, int id) throws IOException {
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

    private byte[] sendGet(byte[] clientIp, int clientPort, int id, int part) throws IOException {
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

    private void doQuery(Socket socket) {
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

    private void doStat(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
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

    private void doGet(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        int id = inputStream.readInt();
        int part = inputStream.readInt();

        byte[] buffer = new byte[PART_SIZE];

        FileInfo file = filesById.get(id);
        file.getFile().seek(part * (long) PART_SIZE);
        file.getFile().read(buffer);

        outputStream.write(buffer);
    }
}
