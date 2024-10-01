import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    // HashMap para armazenar os usuários conectados e seus fluxos de saída de texto
    private static HashMap<String, PrintWriter> users = new HashMap<>();
    // HashMap para armazenar os fluxos de saída de arquivos dos usuários
    private static HashMap<String, ObjectOutputStream> fileStreams = new HashMap<>();

    public static void main(String[] args) {
        // Tenta iniciar um servidor escutando na porta 6924
        try (ServerSocket serverSocket = new ServerSocket(6924)) {
            // Laço infinito para aceitar conexões de novos clientes
            while (true) {
                // Cria uma nova thread para cada cliente que se conecta
                new ClientThread(serverSocket.accept()).start();
            }
        } catch (IOException e) {
            System.out.println(e.getMessage()); // Imprime qualquer erro de IO
        }
    }

    // Classe interna que representa uma thread para lidar com a comunicação de cada cliente
    public static class ClientThread extends Thread {
        private BufferedReader in;  // Leitura de dados do cliente
        private PrintWriter out;  // Escrita de dados para o cliente
        private ObjectOutputStream fileOut;  // Fluxo de saída para envio de arquivos
        private ObjectInputStream fileIn;  // Fluxo de entrada para recebimento de arquivos
        private Socket socket;  // Socket do cliente
        private String command;  // Comando enviado pelo cliente
        private String username;  // Nome de usuário do cliente

        // Construtor que recebe o socket do cliente
        public ClientThread(Socket socket) {
            this.socket = socket;
        }

        // Método principal da thread que lida com a comunicação do cliente
        public void run() {
            try {
                // Inicializa os fluxos de entrada e saída
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                fileOut = new ObjectOutputStream(socket.getOutputStream());
                fileIn = new ObjectInputStream(socket.getInputStream());

                // Laço para processar os comandos do cliente
                while (true) {
                    // Envia uma mensagem inicial de ajuda ao cliente
                    out.println("/help for commands");
                    command = in.readLine();  // Lê o comando do cliente
                    if (command == null) {
                        return;  // Se o comando for nulo, finaliza a conexão
                    }

                    // Trata o comando recebido
                    switch (command.split(" ")[0]) {
                        case "/help":
                            // Lista de comandos disponíveis para o cliente
                            out.println("Commands:");
                            out.println("/help - display this message");
                            out.println("/register <username> - register your username");
                            out.println("/quit - logout from the server");
                            out.println("/users - display all users");
                            out.println("/msg <username> <message> - send a private message to a user");
                            out.println("/file <username> <file> - send a file to a user");
                            break;

                        case "/register":
                            // Comando para registrar um novo nome de usuário
                            String[] regParts = command.split(" ");
                            if (regParts.length < 2) {
                                out.println("Invalid command. Usage: /register <username>");
                                break;
                            }
                            username = regParts[1];
                            // Verifica se o nome de usuário já existe
                            if (users.containsKey(username)) {
                                out.println("Username already exists");
                            } else {
                                // Registra o usuário nos mapas de usuários e fluxos de arquivos
                                users.put(username, out);
                                fileStreams.put(username, fileOut);
                                out.println("Username registered");
                            }
                            break;

                        case "/quit":
                            // Comando para desconectar o cliente
                            return;

                        case "/users":
                            // Comando para listar todos os usuários conectados
                            out.println("Users:");
                            for (String user : users.keySet()) {
                                out.println(user);
                            }
                            break;

                        case "/msg":
                            // Comando para enviar uma mensagem privada a outro usuário
                            String[] msgParts = command.split(" ", 3);
                            if (msgParts.length < 3) {
                                out.println("Invalid command. Usage: /msg <username> <message>");
                                break;
                            }
                            String targetUser = msgParts[1];  // Usuário destinatário
                            String message = msgParts[2];  // Mensagem a ser enviada
                            PrintWriter targetOut = users.get(targetUser);  // Busca o fluxo de saída do destinatário
                            if (targetOut != null) {
                                targetOut.println(username + ": " + message);  // Envia a mensagem
                            } else {
                                out.println("User not found");  // Se o usuário não for encontrado
                            }
                            break;

                        case "/file":
                            // Comando para enviar um arquivo a outro usuário
                            String[] fileParts = command.split(" ", 3);
                            if (fileParts.length < 3) {
                                out.println("Invalid command. Usage: /file <username> <file>");
                                break;
                            }
                            String fileUser = fileParts[1];  // Usuário destinatário do arquivo
                            ObjectOutputStream fileTargetOut = fileStreams.get(fileUser);  // Busca o fluxo de saída de arquivo do destinatário
                            if (fileTargetOut != null) {
                                out.println("Sending file to " + fileUser);
                                users.get(fileUser).println("FILE INCOMING from " + username);

                                // Recebe o nome do arquivo e o conteúdo do cliente remetente
                                String fileName = (String) fileIn.readObject();
                                byte[] fileContent = (byte[]) fileIn.readObject();

                                // Envia o arquivo para o destinatário
                                fileTargetOut.writeObject(fileName);
                                fileTargetOut.writeObject(fileContent);
                                fileTargetOut.flush();

                                out.println("File sent successfully to " + fileUser);
                            } else {
                                out.println("User not found");  // Se o usuário não for encontrado
                            }
                            break;
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println(e.getMessage());  // Trata exceções de IO e de classe não encontrada
            } finally {
                // Remove o usuário dos mapas de usuários e fluxos de arquivos ao desconectar
                if (username != null) {
                    users.remove(username);
                    fileStreams.remove(username);
                }
                // Fecha o socket do cliente
                try {
                    socket.close();
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
            }
        }
    }
}
