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
        new Client().start(argsNewFile);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String[] argsList = {"list", "localhost"};
        new Client().start(argsList);
    }
}
