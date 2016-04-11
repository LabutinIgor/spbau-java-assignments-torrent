package ru.spbau.mit;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Scanner;

public class Client {
    private static final int PORT = 8081;

    private static boolean isConnected;
    private static String host;
    private static Map<Integer, FileInfo> filesById;

    public static void main(String[] args) throws IOException {
        Scanner in = new Scanner(System.in);

        while (true) {
            String[] request = in.nextLine().split(" ");
            switch (request[0]) {
                case "connect":
                    connect(request);
                    break;
                case "disconnect":
                    disconnect();
                    break;
                case "list":
                    list();
                    break;
                case "download":
                    download(request);
                    break;
                case "upload":
                    upload(request);
                    break;
                case "exit":
                    return;
                default:
                    printUsage();
                    break;
            }
        }
    }

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("connect <host>");
        System.err.println("disconnect");
        System.err.println("list");
        System.err.println("download <id>");
        System.err.println("upload <path>");
    }

    private static void connect(String[] args) {

    }

    private static void disconnect() {

    }

    private static void list() {

    }

    private static void download(String[] args) {

    }

    private static void upload(String[] args) {

    }


    private void doStat(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {

    }

    private void doGet(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {

    }
}
