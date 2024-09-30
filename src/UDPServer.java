import java.io.*;
import java.net.*;
import java.util.*;

public class UDPServer {
    private static final int SERVER_PORT = 6924;
    private static HashMap<String, InetSocketAddress> users = new HashMap<>();
    private static HashMap<InetSocketAddress, DatagramSocket> userSockets = new HashMap<>();

    public static void main(String[] args) {
        try (DatagramSocket serverSocket = new DatagramSocket(SERVER_PORT)) {
            byte[] receiveBuffer = new byte[4096];
            byte[] sendBuffer;

            System.out.println("Server started");
            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                serverSocket.receive(receivePacket);

                String command = new String(receivePacket.getData(), 0, receivePacket.getLength());
                InetSocketAddress clientAddress = new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort());

                String[] commandParts = command.split(" ", 3);
                String operation = commandParts[0];

                switch (operation) {
                    case "/help":
                        String helpMessage = "Commands:\n"
                                + "/help - display this message\n"
                                + "/register <username> - register your username\n"
                                + "/quit - logout from the server\n"
                                + "/users - display all users\n"
                                + "/msg <username> <message> - send a private message to a user\n"
                                + "/file <username> <filepath> - send a file to a user\n"
                                + "/broadcast <message> - send a message to all users";
                        sendBuffer = helpMessage.getBytes();
                        sendResponse(serverSocket, sendBuffer, clientAddress);
                        break;

                    case "/register":
                        if (commandParts.length < 2) {
                            sendBuffer = "Invalid command. Usage: /register <username>".getBytes();
                        } else {
                            String username = commandParts[1];
                            if (users.containsKey(username)) {
                                sendBuffer = "Username already exists".getBytes();
                            } else {
                                users.put(username, clientAddress);
                                userSockets.put(clientAddress, serverSocket);
                                sendBuffer = "Username registered".getBytes();
                            }
                        }
                        sendResponse(serverSocket, sendBuffer, clientAddress);
                        break;

                    case "/quit":
                        users.values().remove(clientAddress);
                        userSockets.remove(clientAddress);
                        sendBuffer = "You have logged out".getBytes();
                        sendResponse(serverSocket, sendBuffer, clientAddress);
                        break;

                    case "/users":
                        StringBuilder userList = new StringBuilder("Users:\n");
                        for (String user : users.keySet()) {
                            userList.append(user).append("\n");
                        }
                        sendBuffer = userList.toString().getBytes();
                        sendResponse(serverSocket, sendBuffer, clientAddress);
                        break;

                    case "/msg":
                        if (commandParts.length < 3) {
                            sendBuffer = "Invalid command. Usage: /msg <username> <message>".getBytes();
                        } else {
                            String targetUser = commandParts[1];
                            String message = commandParts[2];
                            
                            InetSocketAddress targetAddress = users.get(targetUser);
                            if (targetAddress != null) {
                                String msgToSend = "Message from " + getUsernameByAddress(clientAddress) + ": " + message;
                                sendBuffer = msgToSend.getBytes();
                                sendResponse(userSockets.get(targetAddress), sendBuffer, targetAddress);
                                sendBuffer = "Message sent successfully".getBytes();
                            } else {
                                sendBuffer = "User not found".getBytes();
                            }
                        }
                        sendResponse(serverSocket, sendBuffer, clientAddress);
                        break;

                    case "/file":
                        if (commandParts.length < 3) {
                            sendBuffer = "Invalid command. Usage: /file <username> <filepath>".getBytes();
                        } else {
                            String fileUser = commandParts[1];
                            String fileName = commandParts[2];
                            InetSocketAddress fileUserAddress = users.get(fileUser);

                            if (fileUserAddress != null) {
                                sendBuffer = ("FILE INCOMING from " + getUsernameByAddress(clientAddress)).getBytes();
                                sendResponse(userSockets.get(fileUserAddress), sendBuffer, fileUserAddress);

                                File file = new File(fileName);
                                if (file.exists()) {
                                    FileInputStream fis = new FileInputStream(file);
                                    byte[] fileBuffer = new byte[(int) file.length()];
                                    fis.read(fileBuffer);
                                    fis.close();
                                    sendResponse(userSockets.get(fileUserAddress), fileBuffer, fileUserAddress);
                                    sendBuffer = "File sent successfully".getBytes();
                                } else {
                                    sendBuffer = "File not found".getBytes();
                                }
                            } else {
                                sendBuffer = "User not found".getBytes();
                            }
                        }
                        sendResponse(serverSocket, sendBuffer, clientAddress);
                        break;
                    
                    case "/broadcast":
                        String message = "";
                        for (int i = 1; i < commandParts.length - 1; i++) {
                            message = commandParts[i] + " ";
                        }
                        String broadcastMessage = "Broadcast message from " + getUsernameByAddress(clientAddress) + ": " + message;
                        sendBuffer = broadcastMessage.getBytes();
                        for (InetSocketAddress userAddress : users.values()) {
                            if (!userAddress.equals(clientAddress))
                                sendResponse(userSockets.get(userAddress), sendBuffer, userAddress);
                        }
                        break;
                }
            }
        } catch (IOException e) {
            System.out.println("Error in server: " + e.getMessage());
        }
    }

    private static void sendResponse(DatagramSocket socket, byte[] sendBuffer, InetSocketAddress clientAddress) throws IOException {
        DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, clientAddress.getAddress(), clientAddress.getPort());
        socket.send(sendPacket);
    }

    private static String getUsernameByAddress(InetSocketAddress address) {
        for (Map.Entry<String, InetSocketAddress> entry : users.entrySet()) {
            if (entry.getValue().equals(address)) {
                return entry.getKey();
            }
        }
        return "Unknown";
    }
}
