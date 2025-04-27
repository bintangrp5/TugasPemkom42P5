package socketprogamming;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class FileServer {
    private static ServerSocket serverSocket;
    private static Map<String, ClientHandler> clients = new HashMap<>();

    public static void main(String[] args) {
        JFrame frame = new JFrame("File Server");
        JTextArea statusArea = new JTextArea(15, 40);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new JScrollPane(statusArea));
        frame.pack();
        frame.setVisible(true);

        try {
            serverSocket = new ServerSocket(5000);
            statusArea.append("Server started on port 5000\n");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket, statusArea).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
            statusArea.append("Server error: " + e.getMessage() + "\n");
        }
    }

    static class ClientHandler extends Thread {
        private Socket socket;
        private DataInputStream dis;
        private DataOutputStream dos;
        private String clientName;
        private JTextArea statusArea;

        public ClientHandler(Socket socket, JTextArea statusArea) {
            this.socket = socket;
            this.statusArea = statusArea;
            try {
                dis = new DataInputStream(socket.getInputStream());
                dos = new DataOutputStream(socket.getOutputStream());
                clientName = dis.readUTF();
                synchronized (clients) {
                    clients.put(clientName, this);
                    broadcastClients();
                }
                statusArea.append("Client connected: " + clientName + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void sendFile(String fileName, long fileSize, byte[] fileData) throws IOException {
            dos.writeUTF("FILE");
            dos.writeUTF(fileName);
            dos.writeLong(fileSize);
            dos.write(fileData);
            dos.flush();
        }

        public void sendClientsList(String clientsList) throws IOException {
            dos.writeUTF("CLIENTS");
            dos.writeUTF(clientsList);
            dos.flush();
        }

        private static void broadcastClients() {
            StringBuilder sb = new StringBuilder();
            for (String name : clients.keySet()) {
                sb.append(name).append(",");
            }
            if (sb.length() > 0) sb.setLength(sb.length() - 1); 
            String list = sb.toString();

            for (ClientHandler client : clients.values()) {
                try {
                    client.sendClientsList(list);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void run() {
            try {
                while (true) {
                    String command = dis.readUTF();
                    if (command.equals("SEND")) {
                        String targetClientName = dis.readUTF();
                        String fileName = dis.readUTF();
                        long fileSize = dis.readLong();

                        byte[] fileData = new byte[(int) fileSize];
                        dis.readFully(fileData);

                        ClientHandler targetClient;
                        synchronized (clients) {
                            targetClient = clients.get(targetClientName);
                        }

                        if (targetClient != null) {
                            targetClient.sendFile(fileName, fileSize, fileData);
                            dos.writeUTF("ACK");
                            statusArea.append("File '" + fileName + "' sent from " + clientName + " to " + targetClientName + "\n");
                        } else {
                            dos.writeUTF("Client not found");
                            statusArea.append("Failed to send '" + fileName + "': Target client '" + targetClientName + "' not found.\n");
                        }
                    }
                }
            } catch (IOException e) {
                statusArea.append("Connection with " + clientName + " lost.\n");
            } finally {
                try {
                    socket.close();
                    synchronized (clients) {
                        clients.remove(clientName);
                        broadcastClients();
                    }
                    statusArea.append(clientName + " disconnected.\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
