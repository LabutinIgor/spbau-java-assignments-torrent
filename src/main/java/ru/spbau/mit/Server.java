package ru.spbau.mit;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Server implements AutoCloseable {
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

    public Server() {
        filesById = new HashMap<>();
        clientFiles = new HashMap<>();
    }

    public static void main(String[] args) throws IOException {
        new Server().start();
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
                e.printStackTrace();
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
                    System.err.println("Incorrect query");
            }
        } catch (IOException e) {
            e.printStackTrace();
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
        FileInfo fileInfo = new FileInfo();
        fileInfo.setId(newFileId++);
        fileInfo.setName(inputStream.readUTF());
        fileInfo.setSize(inputStream.readLong());
        filesById.put(fileInfo.getId(), fileInfo);
        outputStream.writeInt(fileInfo.getId());
    }

    private void processSourcesQuery(DataInputStream inputStream, DataOutputStream outputStream)
            throws IOException {
        int id = inputStream.readInt();
        updateClientsOfFile(id);

        outputStream.writeInt(filesById.get(id).getSeeds().size());

        for (ClientInfo clientInfo : filesById.get(id).getSeeds()) {
            outputStream.write(clientInfo.getIp());
            outputStream.writeShort(clientInfo.getPort());
        }
    }

    private void processUpdateQuery(DataInputStream inputStream, DataOutputStream outputStream, byte[] ip)
            throws IOException {
        short port = inputStream.readByte();
        ClientInfo currentClient = new ClientInfo();
        currentClient.setIp(ip);
        currentClient.setPort(port);
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

        outputStream.writeBoolean(true);
    }

}
