package socketprogamming;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class FileClient {
    private static Socket socket;
    private static DataOutputStream dos;
    private static DataInputStream dis;
    private static Map<String, File> receivedFiles = new HashMap<>();
    private static JTextArea statusArea;
    private static JFrame frame;
    private static JComboBox<String> clientListComboBox;
    private static JProgressBar progressBar;

    public static void main(String[] args) {
        frame = new JFrame("File Client");
        JPanel panel = new JPanel();
        JButton sendFileButton = new JButton("Send File");
        JButton openFileButton = new JButton("Open Received File");
        statusArea = new JTextArea(15, 40);
        clientListComboBox = new JComboBox<>();
        progressBar = new JProgressBar(0, 100);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel("Target Client:"));
        panel.add(clientListComboBox);
        panel.add(sendFileButton);
        panel.add(openFileButton);
        panel.add(progressBar);
        panel.add(new JScrollPane(statusArea));
        frame.add(panel);
        frame.pack();
        frame.setVisible(true);

        String serverAddress = JOptionPane.showInputDialog("Enter Server IP Address (e.g., localhost):");
        int port = 5000;

        try {
            socket = new Socket(serverAddress, port);
            dos = new DataOutputStream(socket.getOutputStream());
            dis = new DataInputStream(socket.getInputStream());

            String clientName = JOptionPane.showInputDialog("Enter your name:");
            dos.writeUTF(clientName);

            statusArea.append("Connected to server as " + clientName + "\n");

            sendFileButton.addActionListener(e -> {
                JFileChooser fileChooser = new JFileChooser();
                int result = fileChooser.showOpenDialog(frame);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File fileToSend = fileChooser.getSelectedFile();
                    String targetClient = (String) clientListComboBox.getSelectedItem();
                    if (targetClient != null && !targetClient.isEmpty()) {
                        new Thread(() -> sendFile(fileToSend, targetClient)).start();
                    } else {
                        JOptionPane.showMessageDialog(frame, "Please select a target client!");
                    }
                }
            });

            openFileButton.addActionListener(e -> {
                if (receivedFiles.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "No files received yet.");
                    return;
                }
                String[] options = receivedFiles.keySet().toArray(new String[0]);
                String selected = (String) JOptionPane.showInputDialog(
                        frame, "Select a file to open:", "Open File",
                        JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
                if (selected != null) {
                    try {
                        Desktop.getDesktop().open(receivedFiles.get(selected));
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(frame, "Cannot open file: " + ex.getMessage());
                    }
                }
            });

            new Thread(() -> listen()).start();

        } catch (IOException e) {
            e.printStackTrace();
            statusArea.append("Connection failed: " + e.getMessage() + "\n");
        }
    }

    private static void sendFile(File file, String targetClient) {
        try {
            dos.writeUTF("SEND");
            dos.writeUTF(targetClient);
            dos.writeUTF(file.getName());
            dos.writeLong(file.length());

            long totalSent = 0;
            long totalSize = file.length();
            byte[] buffer = new byte[65536];
            int bytesRead;

            try (FileInputStream fis = new FileInputStream(file)) {
                while ((bytesRead = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                    totalSent += bytesRead;
                    int progress = (int) ((totalSent * 100) / totalSize);
                    SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
                }
            }
            dos.flush();

            String ack = dis.readUTF();
            if ("ACK".equals(ack)) {
                statusArea.append("File '" + file.getName() + "' sent to " + targetClient + "\n");
            } else {
                statusArea.append("Failed to send file to " + targetClient + ": " + ack + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            statusArea.append("Error sending file: " + e.getMessage() + "\n");
        }
    }

    private static void listen() {
        try {
            File receivedDir = new File("received_files");
            if (!receivedDir.exists()) receivedDir.mkdir();

            while (true) {
                String command = dis.readUTF();
                if (command.equals("FILE")) {
                    String fileName = dis.readUTF();
                    long fileSize = dis.readLong();
                    File receivedFile = new File(receivedDir, fileName);

                    byte[] buffer = new byte[65536];
                    try (FileOutputStream fos = new FileOutputStream(receivedFile)) {
                        long totalRead = 0;
                        while (totalRead < fileSize) {
                            int bytesRead = dis.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalRead));
                            if (bytesRead == -1) break;
                            fos.write(buffer, 0, bytesRead);
                            totalRead += bytesRead;
                        }
                    }

                    receivedFiles.put(fileName, receivedFile);
                    statusArea.append("Received file: " + fileName + "\n");

                } else if (command.equals("CLIENTS")) {
                    String list = dis.readUTF();
                    SwingUtilities.invokeLater(() -> {
                        clientListComboBox.removeAllItems();
                        for (String name : list.split(",")) {
                            clientListComboBox.addItem(name);
                        }
                    });
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            statusArea.append("Connection lost: " + e.getMessage() + "\n");
        }
    }
}
