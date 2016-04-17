package ru.spbau.mit;

import java.io.*;
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
    private static final int ARGS_LENGTH_LIST = 2;
    private static final int ARGS_LENGTH_GET = 3;
    private static final int ARGS_LENGTH_NEWFILE = 3;
    private static final int ARGS_LENGTH_RUN = 2;
    private static final int PART_SIZE = 4096;
    private static final String CONFIG_FILE = "config.txt";

    private static String host;
    private static int port;
    private static Map<Integer, FileInfo> filesById;

    public static void main(String[] args) throws IOException {
        loadState();
        Scanner in = new Scanner(System.in);

        String[] request = in.nextLine().split(" ");
        switch (request[0]) {
            case "list":
                list(request);
                break;
            case "get":
                get(request);
                break;
            case "newfile":
                newfile(request);
                break;
            case "run":
                run(request);
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
            DataInputStream in = new DataInputStream(new FileInputStream(config));

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
        DataOutputStream out = new DataOutputStream(new FileOutputStream(CONFIG_FILE));

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

    private static void list(String[] args) {
        if (args.length != ARGS_LENGTH_LIST) {
            System.out.println("Wrong arguments");
            printUsage();
            return;
        }
        try {
            Socket socket = new Socket(args[1], PORT);
            socket.getOutputStream().write(LIST);

            DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            int cnt = inputStream.readInt();

            for (int i = 0; i < cnt; i++) {
                FileInfo file = new FileInfo();
                file.id = inputStream.readInt();
                file.name = inputStream.readUTF();
                file.size = inputStream.readLong();
                System.out.println("id: " + file.id + " name: " + file.name + " size: " + file.size);
            }
        } catch (IOException e) {
            System.out.println("Error");
        }
    }

    private static void get(String[] args) {
        if (args.length != ARGS_LENGTH_GET) {
            System.out.println("Wrong arguments");
            printUsage();
            return;
        }
        int id = Integer.parseInt(args[2]);
        FileInfo file = new FileInfo();
        file.id = id;
        file.startedDownloading = false;
        filesById.put(id, file);
    }

    private static void newfile(String[] args) {
        if (args.length != ARGS_LENGTH_NEWFILE) {
            System.out.println("Wrong arguments");
            printUsage();
            return;
        }
        try {
            Socket socket = new Socket(args[1], PORT);

            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

            FileInfo file = new FileInfo();
            file.file = new RandomAccessFile(args[2], "r");
            file.name = args[2];
            file.size = file.file.length();

            outputStream.writeByte(UPLOAD);
            outputStream.writeUTF(file.name);
            outputStream.writeLong(file.size);

            DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            file.id = inputStream.readInt();
            filesById.put(file.id, file);

            socket.close();
        } catch (IOException e) {
            System.out.println("Error");
        }
    }

    private static void run(String[] args) {
        if (args.length != ARGS_LENGTH_RUN) {
            System.out.println("Wrong arguments");
            printUsage();
            return;
        }

        host = args[1];


    }

    private boolean sendUpdate() throws IOException {
        Socket socket = new Socket(host, PORT);
        DataInputStream inputStream = new DataInputStream(socket.getInputStream());
        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

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

    private List<Integer> sendStat(String clientIp, int clientPort, int id) throws IOException {
        Socket socket = new Socket(clientIp, clientPort);
        DataInputStream inputStream = new DataInputStream(socket.getInputStream());
        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

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

    private byte[] sendGet(String clientIp, int clientPort, int id, int part) throws IOException {
        Socket socket = new Socket(clientIp, clientPort);
        DataInputStream inputStream = new DataInputStream(socket.getInputStream());
        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

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

    private void doStat(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
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

    private void doGet(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        int id = inputStream.readInt();
        int part = inputStream.readInt();

        byte[] buffer = new byte[PART_SIZE];

        FileInfo file = filesById.get(id);
        file.file.seek(part * (long) PART_SIZE);
        file.file.read(buffer);

        outputStream.write(buffer);
    }
}
