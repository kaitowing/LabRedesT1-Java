import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.util.Scanner;

public class UDPChatClient {
    // Endereço IP e porta do servidor
    private static final String SERVER_ADDRESS = "127.0.0.1";  // Endereço do servidor (localhost)
    private static final int SERVER_PORT = 6924;  // Porta na qual o servidor está escutando
    private static final int BUFFER_SIZE = 4094;  // Tamanho do buffer de recepção de dados

    public static void main(String[] args) {
        // Conexão ao servidor usando um socket UDP e configuração do scanner para leitura de comandos do usuário
        try (DatagramSocket clientSocket = new DatagramSocket();  // Socket UDP do cliente
                Scanner scanner = new Scanner(System.in)) {

            InetAddress serverAddress = InetAddress.getByName(SERVER_ADDRESS);  // Obtém o endereço do servidor

            System.out.println("Connected to the server.");  // Confirmação de conexão ao servidor

            // Criação de uma thread para receber mensagens do servidor de forma assíncrona
            Thread messageReceiver = new Thread(new MessageReceiver(clientSocket));
            messageReceiver.start();

            // Loop principal para enviar comandos ao servidor
            while (true) {
                System.out.print("> ");
                String userInput = scanner.nextLine();  // Lê a entrada do usuário

                // Verifica se o comando é de envio de arquivo
                if (userInput.startsWith("/file")) {
                    String[] commandParts = userInput.split(" ", 3);
                    if (commandParts.length < 3) {
                        System.out.println("Usage: /file <username> <filepath>");  // Valida o comando
                    } else {
                        String fileName = commandParts[2];
                        File file = new File(fileName);

                        if (file.exists()) {
                            sendCommand(clientSocket, userInput, serverAddress);  // Envia o comando para o servidor
                            sendFile(file, fileName, clientSocket, serverAddress);  // Envia o arquivo para o servidor
                        } else {
                            System.out.println("File not found: " + fileName);  // Arquivo não encontrado
                        }
                    }
                } else {
                    // Envia comandos regulares para o servidor (como /msg ou /users)
                    sendCommand(clientSocket, userInput, serverAddress);
                }

                // Verifica se o comando é /quit para encerrar a conexão
                if (userInput.equals("/quit")) {
                    break;
                }
            }

            // Interrompe a thread de recepção de mensagens ao sair
            messageReceiver.interrupt();

        } catch (IOException e) {
            System.out.println("Error connecting to the server: " + e.getMessage());  // Trata erros de conexão
        }
    }

    // Método para enviar comandos ao servidor
    private static void sendCommand(DatagramSocket socket, String command, InetAddress serverAddress)
            throws IOException {
        byte[] sendBuffer = command.getBytes();  // Converte o comando em bytes
        DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, serverAddress, SERVER_PORT);
        socket.send(sendPacket);  // Envia o pacote para o servidor
    }

    // Método para enviar um arquivo ao servidor
    private static void sendFile(File file, String fileName, DatagramSocket socket, InetAddress serverAddress)
            throws IOException {
        byte[] fileBytes = new byte[(int) file.length()];  // Cria um array de bytes com o tamanho do arquivo
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis);
        bis.read(fileBytes, 0, fileBytes.length);  // Lê o arquivo no array de bytes

        // Cria e envia um pacote contendo o arquivo
        DatagramPacket filePacket = new DatagramPacket(fileBytes, fileBytes.length, serverAddress, SERVER_PORT);
        socket.send(filePacket);
        bis.close();  // Fecha o buffer de leitura
    }

    // Classe interna que representa a thread para receber mensagens do servidor
    static class MessageReceiver implements Runnable {
        private DatagramSocket socket;  // Socket para recepção de mensagens
        private byte[] receiveBuffer = new byte[BUFFER_SIZE];  // Buffer para armazenar as mensagens recebidas

        public MessageReceiver(DatagramSocket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                // Loop de recepção de mensagens enquanto a thread não for interrompida
                while (!Thread.currentThread().isInterrupted()) {
                    DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                    socket.receive(receivePacket);  // Recebe o pacote do servidor
                    String response = new String(receivePacket.getData(), 0, receivePacket.getLength());

                    // Verifica se a mensagem recebida é um arquivo
                    if (response.startsWith("FILE INCOMING")) {
                        receiveFile(socket, response.split(" ")[3]);  // Recebe o arquivo
                    } else {
                        System.out.println(response);  // Exibe outras mensagens recebidas
                    }
                }
            } catch (IOException e) {
                System.out.println("Error receiving message from server: " + e.getMessage());  // Trata erros de recepção
            }
        }

        // Método para receber um arquivo enviado pelo servidor
        private void receiveFile(DatagramSocket socket, String sender) throws IOException {
            byte[] fileBuffer = new byte[BUFFER_SIZE];  // Cria um buffer para armazenar o arquivo
            DatagramPacket filePacket = new DatagramPacket(fileBuffer, fileBuffer.length);
            socket.receive(filePacket);  // Recebe o pacote contendo o arquivo

            // Gera um nome de arquivo baseado no remetente e na data/hora atual
            String fileName = "src/received_" + LocalDateTime.now().toString().replace(":", "-") + ".txt";
            FileOutputStream fos = new FileOutputStream(fileName);
            fos.write(filePacket.getData(), 0, filePacket.getLength());  // Escreve o conteúdo do arquivo no sistema local
            fos.close();

            System.out.println("File received from " + sender + " and saved as: " + fileName);  // Confirmação de recebimento
        }
    }
}
