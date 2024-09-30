import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private static HashMap<String, PrintWriter> users = new HashMap<>();
    private static HashMap<String, ObjectOutputStream> fileStreams = new HashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(6924)) {
            while (true) {
                new ClientThread(serverSocket.accept()).start();
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public static class ClientThread extends Thread {
        private BufferedReader in;
        private PrintWriter out;
        private ObjectOutputStream fileOut;
        private ObjectInputStream fileIn;
        private Socket socket;
        private String command;
        private String username;

        public ClientThread(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                fileOut = new ObjectOutputStream(socket.getOutputStream());
                fileIn = new ObjectInputStream(socket.getInputStream());

                while (true) {
                    out.println("/help for commands");
                    command = in.readLine();
                    if (command == null) {
                        return;
                    }
                    switch (command.split(" ")[0]) {
                        case "/help":
                            out.println("Commands:");
                            out.println("/help - display this message");
                            out.println("/register <username> - register your username");
                            out.println("/quit - logout from the server");
                            out.println("/users - display all users");
                            out.println("/msg <username> <message> - send a private message to a user");
                            out.println("/file <username> <file> - send a file to a user");
                            break;

                        case "/register":
                            String[] regParts = command.split(" ");
                            if (regParts.length < 2) {
                                out.println("Invalid command. Usage: /register <username>");
                                break;
                            }
                            username = regParts[1];
                            if (users.containsKey(username)) {
                                out.println("Username already exists");
                            } else {
                                users.put(username, out);
                                fileStreams.put(username, fileOut);
                                out.println("Username registered");
                            }
                            break;

                        case "/quit":
                            return;

                        case "/users":
                            out.println("Users:");
                            for (String user : users.keySet()) {
                                out.println(user);
                            }
                            break;

                        case "/msg":
                            String[] msgParts = command.split(" ", 3);
                            if (msgParts.length < 3) {
                                out.println("Invalid command. Usage: /msg <username> <message>");
                                break;
                            }
                            String targetUser = msgParts[1];
                            String message = msgParts[2];
                            PrintWriter targetOut = users.get(targetUser);
                            if (targetOut != null) {
                                targetOut.println(username + ": " + message);
                            } else {
                                out.println("User not found");
                            }
                            break;

                        case "/file":
                            String[] fileParts = command.split(" ", 3);
                            if (fileParts.length < 3) {
                                out.println("Invalid command. Usage: /file <username> <file>");
                                break;
                            }
                            String fileUser = fileParts[1];
                            ObjectOutputStream fileTargetOut = fileStreams.get(fileUser);
                            if (fileTargetOut != null) {
                                out.println("Sending file to " + fileUser);
                                users.get(fileUser).println("FILE INCOMING from " + username);

                                // Recebe o arquivo do cliente remetente
                                String fileName = (String) fileIn.readObject();
                                byte[] fileContent = (byte[]) fileIn.readObject();

                                // Envia o arquivo para o cliente destinatário
                                fileTargetOut.writeObject(fileName);
                                fileTargetOut.writeObject(fileContent);
                                fileTargetOut.flush();

                                out.println("File sent successfully to " + fileUser);
                            } else {
                                out.println("User not found");
                            }
                            break;
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println(e.getMessage());
            } finally {
                if (username != null) {
                    users.remove(username);
                    fileStreams.remove(username);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
            }
        }
    }
}
