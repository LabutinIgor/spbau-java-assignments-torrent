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
    private static final String CONFIG_FILE = "config.txt";

    private String host;
    private int port;
    private Map<Integer, FileInfo> filesById;

    public static void main(String[] args) throws IOException {
        new Client().start(args);
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
        File config = new File(CONFIG_FILE);
        filesById = new HashMap<>();
        if (config.exists()) {
            final DataInputStream in = new DataInputStream(new FileInputStream(config));

            int cnt = in.readInt();
            for (int i = 0; i < cnt; i++) {
                FileInfo file = new FileInfo();
                file.setId(in.readInt());
                if (in.readBoolean()) {
                    file.setName(in.readUTF());
                    file.setSize(in.readLong());
                    byte[] parts = new byte[file.getPartsCnt()];
                    file.setIsDownloadedParts(new boolean[file.getPartsCnt()]);
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
            saveState();
        }
    }

    private void saveState() throws IOException {
        final DataOutputStream out = new DataOutputStream(new FileOutputStream(CONFIG_FILE));

        out.writeInt(filesById.size());
        for (FileInfo file : filesById.values()) {
            out.writeInt(file.getId());
            out.writeBoolean(file.getFile() != null);
            if (file.getFile() != null) {
                out.writeUTF(file.getName());
                out.writeLong(file.getSize());
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
            FileInfo file = new FileInfo();
            file.setId(inputStream.readInt());
            file.setName(inputStream.readUTF());
            file.setSize(inputStream.readLong());

            files.put(file.getId(), file);
        }
        socket.close();
        return files;
    }

    private void get(String id) {
        FileInfo file = new FileInfo();
        file.setId(Integer.parseInt(id));
        filesById.put(file.getId(), file);
    }

    private void uploadFile(String path) throws IOException {
        Socket socket = new Socket(host, PORT);
        final DataInputStream inputStream = new DataInputStream(socket.getInputStream());

        FileInfo file = new FileInfo();
        file.setFile(new RandomAccessFile(path, "r"));
        file.setName(path);
        file.setSize(file.getFile().length());
        file.setIsDownloadedParts(new boolean[file.getPartsCnt()]);
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
        Map<Integer, FileInfo> filesFromServer = sendListQuery();

        filesById.values().stream().filter(file -> filesFromServer.keySet().contains(file.getId())
                && file.getFile() != null).forEach(file -> {
            FileInfo fileFromServer = filesFromServer.get(file.getId());
            file.setName(fileFromServer.getName());
            file.setSize(fileFromServer.getSize());
            try {
                file.setFile(new RandomAccessFile(file.getName(), "rw"));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            file.setIsDownloadedParts(new boolean[file.getPartsCnt()]);
        });
        saveState();

        TimerTask updateTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    sendUpdateQuery();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        Timer timerToUpdate = new Timer();
        timerToUpdate.schedule(updateTask, 0, UPDATE_TIME);

        //Thread to download
        new Thread(() -> {
            while (true) {
                try {
                    for (FileInfo file : filesById.values()) {
                        if (file.getFile() != null && file.getCntDownloadedParts() != file.getPartsCnt()) {
                            List<ClientInfo> clients = sendSourcesQuery(file.getId());
                            for (ClientInfo client : clients) {
                                List<Integer> parts = sendStatQuery(client.getIp(), client.getPort(),
                                        file.getId());
                                for (Integer part : parts) {
                                    if (!file.getIsDownloadedPart(part)) {
                                        file.setIsDownloadedPart(part, true);
                                        file.setCntDownloadedParts(file.getCntDownloadedParts() + 1);
                                        byte[] buffer = sendGetQuery(client.getIp(), client.getPort(),
                                                file.getId(), part);
                                        file.getFile().seek(part * (long) PART_SIZE);
                                        file.getFile().write(buffer);
                                        saveState();
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

        while (true) {
            try {
                ServerSocket serverSocket = new ServerSocket(0);
                port = serverSocket.getLocalPort();
                while (true) {
                    Socket socket = serverSocket.accept();
                    if (socket == null) {
                        break;
                    }
                    processQuery(socket);
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean sendUpdateQuery() throws IOException {
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

    private List<ClientInfo> sendSourcesQuery(int id) throws IOException {
        Socket socket = new Socket(host, PORT);
        final DataInputStream inputStream = new DataInputStream(socket.getInputStream());
        final DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

        outputStream.writeByte(SOURCES);
        outputStream.writeInt(id);

        List<ClientInfo> clients = new ArrayList<>();
        int cnt = inputStream.readInt();
        for (int i = 0; i < cnt; i++) {
            ClientInfo client = new ClientInfo();
            client.setIp(new byte[4]);
            if (inputStream.read(client.getIp()) != 4) {
                throw new IOException("Error in sources");
            }
            client.setPort(inputStream.readByte());

            clients.add(client);
        }

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

    private byte[] sendGetQuery(byte[] clientIp, int clientPort, int id, int part) throws IOException {
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

    private void processQuery(Socket socket) {
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
        } catch (IOException e) {
            e.printStackTrace();
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

        byte[] buffer = new byte[PART_SIZE];

        FileInfo file = filesById.get(id);
        file.getFile().seek(part * (long) PART_SIZE);
        file.getFile().read(buffer);

        outputStream.write(buffer);
    }
}
