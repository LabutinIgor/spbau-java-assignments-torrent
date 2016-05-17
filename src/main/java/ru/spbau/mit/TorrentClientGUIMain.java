package ru.spbau.mit;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public final class TorrentClientGUIMain {
    private static final long UPDATE_TIME = 1000;
    private static final String HOST = "localhost";
    private static final String CONFIG_FILE = "config" + new Random().nextInt(1000) + ".txt";

    private static TorrentClientMain client;
    private static JFileChooser fileChooser = new JFileChooser();
    private static JTable tableDownloadingFiles;
    private static DefaultTableModel model;

    private TorrentClientGUIMain() {
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Torrent client");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        final JMenuBar menuBar = buildMenuBar();
        frame.setJMenuBar(menuBar);
        start();

        model = new DefaultTableModel() {
            private String[] header = {"name", "progress"};

            @Override
            public int getColumnCount() {
                return header.length;
            }

            @Override
            public String getColumnName(int index) {
                return header[index];
            }
        };

        tableDownloadingFiles = new JTable(model);
        frame.add(new JScrollPane(tableDownloadingFiles));

        TimerTask updateTask = new TimerTask() {
            @Override
            public void run() {
                updateDownloadingFiles();
            }
        };
        java.util.Timer timerToUpdate = new java.util.Timer();
        timerToUpdate.schedule(updateTask, 0, UPDATE_TIME);

        frame.setSize(800, 600);
        frame.setVisible(true);
    }

    private static JMenuBar buildMenuBar() {
        JMenu fileMenu = new JMenu("File");

        JMenuItem downloadItem = new JMenuItem("download");
        downloadItem.addActionListener(e -> download());
        fileMenu.add(downloadItem);

        JMenuItem uploadItem = new JMenuItem("upload");
        uploadItem.addActionListener(e -> upload());
        fileMenu.add(uploadItem);

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(fileMenu);
        return menuBar;
    }

    private static void download() {
        final JFrame frame = new JFrame("Choose files");

        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        stop();
        try {
            TorrentClientMain client = new TorrentClientMain(CONFIG_FILE);
            Map<Integer, FileInfo> files = client.sendListQuery();

            DefaultTableModel model = new DefaultTableModel(getDataForServerFilesTable(files),
                    new String[]{"id", "name", "download"});
            JTable table = new JTable(model) {
                @Override
                public boolean isCellEditable(int row, int col) {
                    switch (col) {
                        case 0:
                            return false;
                        case 1:
                            return false;
                        case 2:
                            return true;
                        default:
                            return false;
                    }
                }

                @Override
                public Class getColumnClass(int column) {
                    switch (column) {
                        case 0:
                            return String.class;
                        case 1:
                            return String.class;
                        case 2:
                            return Boolean.class;
                        default:
                            return Boolean.class;
                    }
                }
            };

            Container pane = frame.getContentPane();
            pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
            pane.add(new JScrollPane(table));

            JButton button = new JButton("download");
            button.setAlignmentX(Component.CENTER_ALIGNMENT);
            button.addActionListener(e -> {
                stop();
                for (int i = 0; i < table.getModel().getRowCount(); i++) {
                    if (table.getModel().getValueAt(i, 2).equals(true)) {
                        try {
                            client.start(new String[]{"get", HOST, table.getModel().getValueAt(i, 0).toString()});
                        } catch (IOException e1) {
                            System.err.println("Error while get query in client");
                        }
                    }
                }

                start();
                frame.dispose();
            });
            pane.add(button);
        } catch (IOException e) {
            System.err.println("Error while doing list in client");
        }

        start();

        frame.pack();
        frame.setVisible(true);
    }

    private static Object[][] getDataForServerFilesTable(Map<Integer, FileInfo> files) {
        Object[][] data = new Object[files.size()][3];
        int cnt = 0;
        for (FileInfo file : files.values()) {
            data[cnt][0] = file.getId();
            data[cnt][1] = file.getName();
            data[cnt][2] = false;
            cnt++;
        }
        return data;
    }

    private static void upload() {
        int ret = fileChooser.showOpenDialog(new JPanel());
        if (ret == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            stop();
            try {
                new TorrentClientMain(CONFIG_FILE).start(new String[]{"newfile", HOST, file.getPath()});
            } catch (IOException e) {
                System.err.println("Error while running client");
            }

            start();
        }
    }

    private static void updateDownloadingFiles() {
        List<FileInfo> files = new TorrentClientMain(CONFIG_FILE).getFiles();

        while (model.getRowCount() > 0) {
            model.removeRow(0);
        }
        for (FileInfo file : files) {
            model.addRow(new Object[]{file.getName(), "загруженно "
                    + file.getCntDownloadedParts() * 100 / file.getPartsCnt() + " %"});
        }

        tableDownloadingFiles.repaint();
    }

    private static void start() {
        if (client == null) {
            client = new TorrentClientMain(CONFIG_FILE);
            new Thread(() -> {
                try {
                    client.start(new String[]{"run", HOST});
                } catch (IOException e) {
                    System.err.println("Error while running client");
                }
            }).start();
        }
    }

    private static void stop() {
        if (client != null) {
            client.stop();
            client = null;
        }
    }
}
