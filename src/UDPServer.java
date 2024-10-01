import java.io.*;
import java.net.*;
import java.util.*;

public class UDPServer {
    private static final int SERVER_PORT = 6924;  // Porta na qual o servidor está escutando
    private static HashMap<String, InetSocketAddress> users = new HashMap<>();  // Mapa para armazenar usuários registrados e seus endereços
    private static HashMap<InetSocketAddress, DatagramSocket> userSockets = new HashMap<>();  // Mapa de endereços de usuários para os sockets

    public static void main(String[] args) {
        try (DatagramSocket serverSocket = new DatagramSocket(SERVER_PORT)) {  // Criação do socket do servidor
            byte[] receiveBuffer = new byte[4096];  // Buffer para armazenar os dados recebidos
            byte[] sendBuffer;  // Buffer para armazenar os dados a serem enviados

            System.out.println("Server started");  // Mensagem de inicialização do servidor
            while (true) {
                // Cria um pacote para receber dados
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                serverSocket.receive(receivePacket);  // Recebe o pacote de dados

                // Extrai a mensagem e o endereço do cliente
                String command = new String(receivePacket.getData(), 0, receivePacket.getLength());
                InetSocketAddress clientAddress = new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort());

                // Divide a mensagem em partes para identificar o comando e os parâmetros
                String[] commandParts = command.split(" ", 3);
                String operation = commandParts[0];

                // Processa o comando baseado na operação solicitada
                switch (operation) {
                    case "/help":
                        // Comando para exibir uma lista de comandos disponíveis
                        String helpMessage = "Commands:\n"
                                + "/help - display this message\n"
                                + "/register <username> - register your username\n"
                                + "/quit - logout from the server\n"
                                + "/users - display all users\n"
                                + "/msg <username> <message> - send a private message to a user\n"
                                + "/file <username> <filepath> - send a file to a user\n"
                                + "/broadcast <message> - send a message to all users";
                        sendBuffer = helpMessage.getBytes();  // Converte a mensagem para bytes
                        sendResponse(serverSocket, sendBuffer, clientAddress);  // Envia a resposta ao cliente
                        break;

                    case "/register":
                        // Comando para registrar um nome de usuário
                        if (commandParts.length < 2) {
                            sendBuffer = "Invalid command. Usage: /register <username>".getBytes();
                        } else {
                            String username = commandParts[1];  // Nome de usuário fornecido
                            if (users.containsKey(username)) {
                                sendBuffer = "Username already exists".getBytes();
                            } else {
                                // Registra o usuário com o endereço do cliente
                                users.put(username, clientAddress);
                                userSockets.put(clientAddress, serverSocket);
                                sendBuffer = "Username registered".getBytes();
                            }
                        }
                        sendResponse(serverSocket, sendBuffer, clientAddress);  // Envia resposta ao cliente
                        break;

                    case "/quit":
                        // Comando para desconectar o usuário
                        users.values().remove(clientAddress);  // Remove o usuário do mapa
                        userSockets.remove(clientAddress);  // Remove o socket do usuário
                        sendBuffer = "You have logged out".getBytes();
                        sendResponse(serverSocket, sendBuffer, clientAddress);  // Confirmação de logout
                        break;

                    case "/users":
                        // Comando para listar os usuários conectados
                        StringBuilder userList = new StringBuilder("Users:\n");
                        for (String user : users.keySet()) {
                            userList.append(user).append("\n");  // Adiciona cada nome de usuário à lista
                        }
                        sendBuffer = userList.toString().getBytes();
                        sendResponse(serverSocket, sendBuffer, clientAddress);  // Envia a lista de usuários
                        break;

                    case "/msg":
                        // Comando para enviar uma mensagem privada a um usuário
                        if (commandParts.length < 3) {
                            sendBuffer = "Invalid command. Usage: /msg <username> <message>".getBytes();
                        } else {
                            String targetUser = commandParts[1];  // Destinatário da mensagem
                            String message = commandParts[2];  // Conteúdo da mensagem
                            
                            InetSocketAddress targetAddress = users.get(targetUser);  // Endereço do usuário alvo
                            if (targetAddress != null) {
                                // Monta a mensagem e envia para o destinatário
                                String msgToSend = "Message from " + getUsernameByAddress(clientAddress) + ": " + message;
                                sendBuffer = msgToSend.getBytes();
                                sendResponse(userSockets.get(targetAddress), sendBuffer, targetAddress);  // Envia a mensagem para o usuário
                                sendBuffer = "Message sent successfully".getBytes();
                            } else {
                                sendBuffer = "User not found".getBytes();  // Caso o usuário não seja encontrado
                            }
                        }
                        sendResponse(serverSocket, sendBuffer, clientAddress);  // Envia a resposta ao remetente
                        break;

                    case "/file":
                        // Comando para enviar um arquivo a um usuário
                        if (commandParts.length < 3) {
                            sendBuffer = "Invalid command. Usage: /file <username> <filepath>".getBytes();
                        } else {
                            String fileUser = commandParts[1];  // Usuário destinatário do arquivo
                            String fileName = commandParts[2];  // Nome do arquivo
                            InetSocketAddress fileUserAddress = users.get(fileUser);  // Endereço do destinatário

                            if (fileUserAddress != null) {
                                // Notifica o destinatário sobre o envio do arquivo
                                sendBuffer = ("FILE INCOMING from " + getUsernameByAddress(clientAddress)).getBytes();
                                sendResponse(userSockets.get(fileUserAddress), sendBuffer, fileUserAddress);

                                // Tenta enviar o arquivo
                                File file = new File(fileName);
                                if (file.exists()) {
                                    FileInputStream fis = new FileInputStream(file);
                                    byte[] fileBuffer = new byte[(int) file.length()];  // Lê o arquivo para um buffer de bytes
                                    fis.read(fileBuffer);
                                    fis.close();
                                    sendResponse(userSockets.get(fileUserAddress), fileBuffer, fileUserAddress);  // Envia o arquivo
                                    sendBuffer = "File sent successfully".getBytes();
                                } else {
                                    sendBuffer = "File not found".getBytes();  // Caso o arquivo não seja encontrado
                                }
                            } else {
                                sendBuffer = "User not found".getBytes();  // Caso o destinatário não seja encontrado
                            }
                        }
                        sendResponse(serverSocket, sendBuffer, clientAddress);  // Envia resposta ao remetente
                        break;
                    
                    case "/broadcast":
                        // Comando para enviar uma mensagem para todos os usuários conectados
                        String message = "";
                        for (int i = 1; i < commandParts.length - 1; i++) {
                            message = commandParts[i] + " ";
                        }
                        String broadcastMessage = "Broadcast message from " + getUsernameByAddress(clientAddress) + ": " + message;
                        sendBuffer = broadcastMessage.getBytes();
                        // Envia a mensagem para todos os usuários conectados, exceto o remetente
                        for (InetSocketAddress userAddress : users.values()) {
                            if (!userAddress.equals(clientAddress))
                                sendResponse(userSockets.get(userAddress), sendBuffer, userAddress);
                        }
                        break;
                }
            }
        } catch (IOException e) {
            System.out.println("Error in server: " + e.getMessage());  // Tratamento de erros de IO
        }
    }

    // Método auxiliar para enviar uma resposta para o cliente
    private static void sendResponse(DatagramSocket socket, byte[] sendBuffer, InetSocketAddress clientAddress) throws IOException {
        DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, clientAddress.getAddress(), clientAddress.getPort());
        socket.send(sendPacket);  // Envia o pacote para o cliente
    }

    // Método auxiliar para obter o nome de usuário a partir de um endereço IP e porta
    private static String getUsernameByAddress(InetSocketAddress address) {
        for (Map.Entry<String, InetSocketAddress> entry : users.entrySet()) {
            if (entry.getValue().equals(address)) {
                return entry.getKey();  // Retorna o nome do usuário associado ao endereço
            }
        }
        return "Unknown";  // Caso o endereço não seja encontrado
    }
}
