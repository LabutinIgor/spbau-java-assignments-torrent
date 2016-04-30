package ru.spbau.mit;

import org.junit.Test;

import java.io.IOException;

public class Tests {

    @Test
    public void testUpload() throws IOException {
        Thread server = new Thread(() -> {
            try {
                new Server().start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        server.start();

        String[] argsNewFile = {"newfile", "localhost", "a.txt"};
        new Client("config1.txt").start(argsNewFile);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String[] argsList = {"list", "localhost"};
        String[] argsRun = {"run", "localhost"};
        String[] argsGet = {"get", "localhost", "0"};

        new Client("config1.txt").start(argsList);

        Thread client1 = new Thread(() -> {
            try {
                new Client("config1.txt").start(argsRun);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        client1.start();

        new Client("config2.txt").start(argsGet);
        System.err.println("RUN!!!");
        new Client("config2.txt").start(argsRun);
    }
}
