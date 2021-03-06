package ru.spbau.mit;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class TorrentTrackerMain implements AutoCloseable {
    private static final int PORT = 8081;
    private static final long UPDATE_TIME = 60000;
    private static final byte LIST = 1;
    private static final byte UPLOAD = 2;
    private static final byte SOURCES = 3;
    private static final byte UPDATE = 4;

    private int newFileId = 0;
    private ServerSocket serverSocket;
    private Map<Integer, FileInfo> filesById;
    private Map<ClientInfo, List<Integer>> clientFiles;

    public TorrentTrackerMain() {
        filesById = new HashMap<>();
        clientFiles = new HashMap<>();
    }

    public static void main(String[] args) throws IOException {
        new TorrentTrackerMain().start();
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(PORT);
        new Thread(this::handleConnections).start();
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
    }

    private void handleConnections() {
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                if (socket == null) {
                    break;
                }
                processQuery(socket);
                socket.close();
            } catch (IOException e) {
                System.err.println("Error in handling connection in server");
                break;
            }
        }
    }

    private void updateClientsOfFile(int id) {
        Set<ClientInfo> newClients = new HashSet<>();
        for (ClientInfo clientInfo : filesById.get(id).getSeeds()) {
            if (clientInfo.getLastUpdateTime() + UPDATE_TIME < System.currentTimeMillis()) {
                removeClient(clientInfo);
            } else {
                newClients.add(clientInfo);
            }
        }
        filesById.get(id).setSeeds(newClients);
    }

    private void removeClient(ClientInfo clientInfo) {
        for (int id : clientFiles.get(clientInfo)) {
            filesById.get(id).getSeeds().remove(clientInfo);
        }
        clientFiles.remove(clientInfo);
    }

    private void processQuery(Socket socket) {
        try (DataInputStream inputStream = new DataInputStream(socket.getInputStream());
             DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream())) {
            byte request = inputStream.readByte();
            switch (request) {
                case LIST:
                    processListQuery(outputStream);
                    break;
                case UPLOAD:
                    processUploadQuery(inputStream, outputStream);
                    break;
                case SOURCES:
                    processSourcesQuery(inputStream, outputStream);
                    break;
                case UPDATE:
                    processUpdateQuery(inputStream, outputStream, socket.getInetAddress().getAddress());
                    break;
                default:
                    System.err.println("Incorrect query to server: " + request);
            }
        } catch (IOException e) {
            System.err.println("Error in processing query in server");
        }
    }

    private void processListQuery(DataOutputStream outputStream) throws IOException {
        outputStream.writeInt(filesById.size());
        for (FileInfo file : filesById.values()) {
            outputStream.writeInt(file.getId());
            outputStream.writeUTF(file.getName());
            outputStream.writeLong(file.getSize());
        }
    }

    private void processUploadQuery(DataInputStream inputStream, DataOutputStream outputStream)
            throws IOException {
        int id = newFileId++;
        String name = inputStream.readUTF();
        long size = inputStream.readLong();
        FileInfo fileInfo = new FileInfo(id, name, size);
        fileInfo.setSeeds(new HashSet<>());
        filesById.put(id, fileInfo);
        outputStream.writeInt(fileInfo.getId());
    }

    private void processSourcesQuery(DataInputStream inputStream, DataOutputStream outputStream)
            throws IOException {
        int id = inputStream.readInt();
        updateClientsOfFile(id);

        outputStream.writeInt(filesById.get(id).getSeeds().size());

        for (ClientInfo clientInfo : filesById.get(id).getSeeds()) {
            outputStream.write(clientInfo.getIp());
            outputStream.writeInt(clientInfo.getPort());
        }
    }

    private void processUpdateQuery(DataInputStream inputStream, DataOutputStream outputStream, byte[] ip)
            throws IOException {
        int port = inputStream.readInt();
        ClientInfo currentClient = new ClientInfo(ip, port);
        currentClient.setLastUpdateTime(System.currentTimeMillis());
        if (clientFiles.containsKey(currentClient)) {
            removeClient(currentClient);
        }

        int cntFiles = inputStream.readInt();
        List<Integer> files = new ArrayList<>();
        for (int i = 0; i < cntFiles; i++) {
            files.add(inputStream.readInt());
        }
        clientFiles.put(currentClient, files);
        for (Integer fileId : files) {
            filesById.get(fileId).getSeeds().add(currentClient);
        }

        outputStream.writeBoolean(true);
    }

}
