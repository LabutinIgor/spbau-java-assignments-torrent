package ru.spbau.mit;

import org.junit.Test;

import java.io.IOException;

public class Tests {

    @Test
    public void testUpload() throws IOException {
        Thread server = new Thread(() -> {
            try {
                new TorrentTrackerMain().start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        server.start();

        final String[] argsNewFile = {"newfile", "localhost", "src/test/resources/a.txt"};
        final String[] argsList = {"list", "localhost"};
        final String[] argsRun = {"run", "localhost"};
        final String[] argsGet = {"get", "localhost", "0"};

        new TorrentClientMain("config1.txt").start(argsNewFile);
        new TorrentClientMain("config1.txt").start(argsList);

        Thread client1 = new Thread(() -> {
            try {
                new TorrentClientMain("config1.txt").start(argsRun);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        client1.start();

        Thread client2 = new Thread(() -> {
            try {
                new TorrentClientMain("config2.txt").start(argsGet);
                new TorrentClientMain("config2.txt").start(argsRun);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        client2.start();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
