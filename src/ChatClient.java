import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.util.Scanner;

public class ChatClient {
    // Endereço IP do servidor e porta para conectar (localhost)
    private static final String SERVER_ADDRESS = "127.0.0.1";  // Endereço do servidor (localhost)
    private static final int SERVER_PORT = 6924;  // Porta do servidor

    public static void main(String[] args) {
        // Conecta ao servidor e inicializa os fluxos de entrada e saída
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             ObjectOutputStream fileOut = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream fileIn = new ObjectInputStream(socket.getInputStream());
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connected to the server.");  // Mensagem de confirmação de conexão

            // Cria uma nova thread para receber mensagens do servidor de forma assíncrona
            new Thread(new ReadMessages(in, fileIn)).start();

            String userInput;
            // Loop para ler a entrada do usuário e enviar comandos para o servidor
            while (true) {
                System.out.print("> ");
                userInput = scanner.nextLine();

                // Verifica se o comando é de envio de arquivo
                if (userInput.startsWith("/file")) {
                    String[] commandParts = userInput.split(" ", 3);
                    if (commandParts.length < 3) {
                        System.out.println("Usage: /file <username> <filename>");  // Verifica se o comando está correto
                    } else {
                        String fileName = commandParts[2];
                        File file = new File(fileName);

                        // Verifica se o arquivo existe antes de tentar enviá-lo
                        if (file.exists()) {
                            out.println(userInput);  // Envia o comando de envio de arquivo para o servidor
                            sendFile(file, fileName, fileOut);  // Chama o método para enviar o arquivo
                        } else {
                            System.out.println("File not found: " + fileName);  // Arquivo não encontrado
                        }
                    }
                } else {
                    // Envia outros comandos (como mensagens ou comandos de sistema) para o servidor
                    out.println(userInput);
                }

                // Se o comando for /quit, encerra o cliente
                if (userInput.equals("/quit")) {
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println("Error connecting to the server: " + e.getMessage());  // Erro ao tentar se conectar ao servidor
        }
    }

    // Método para enviar um arquivo ao servidor
    private static boolean sendFile(File file, String fileName, ObjectOutputStream fileOut) {
        try {
            byte[] byteArray = new byte[(int) file.length()];  // Cria um array de bytes com o tamanho do arquivo
            FileInputStream fis = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(fis);
            bis.read(byteArray, 0, byteArray.length);  // Lê o conteúdo do arquivo no array de bytes
            bis.close();

            System.out.println("Sending file: " + fileName);
            fileOut.writeObject(fileName);  // Envia o nome do arquivo
            fileOut.writeObject(byteArray);  // Envia o conteúdo do arquivo
            fileOut.flush();  // Garante que os dados sejam enviados
            System.out.println("File sent.");
        } catch (IOException e) {
            System.out.println("Error sending file: " + e.getMessage());  // Trata erros de envio de arquivo
            return false;
        }
        return true;
    }

    // Classe interna que representa a thread responsável por ler as mensagens do servidor
    private static class ReadMessages implements Runnable {
        private BufferedReader in;  // Fluxo de leitura de texto do servidor
        private ObjectInputStream fileIn;  // Fluxo de leitura de arquivos do servidor

        // Construtor que recebe os fluxos de entrada
        public ReadMessages(BufferedReader in, ObjectInputStream fileIn) {
            this.in = in;
            this.fileIn = fileIn;
        }

        // Método que será executado pela thread para ler as mensagens do servidor
        public void run() {
            try {
                String messageFromServer;
                while ((messageFromServer = in.readLine()) != null) {
                    // Verifica se o servidor está enviando um arquivo
                    if (messageFromServer.startsWith("FILE INCOMING")) {
                        // Recebe o arquivo enviado pelo servidor
                        receiveFile(fileIn, messageFromServer.split(" ")[3]);
                    } else {
                        System.out.println(messageFromServer);  // Exibe outras mensagens do servidor
                    }
                }
            } catch (IOException e) {
                System.out.println("Error reading from server: " + e.getMessage());  // Trata erros de leitura do servidor
            }
        }

        // Método para receber um arquivo do servidor
        private void receiveFile(ObjectInputStream fileIn, String sender) {
            try {
                // Recebe o nome do arquivo e seu conteúdo
                String fileName = (String) fileIn.readObject();
                byte[] fileBytes = (byte[]) fileIn.readObject();

                // Gera um nome para o arquivo baseado no remetente e na data/hora atual
                fileName = "src/" + sender + "_" + LocalDateTime.now().toString().replace(":", "-") + ".txt";
                FileOutputStream fos = new FileOutputStream(fileName);
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                bos.write(fileBytes);  // Escreve o conteúdo do arquivo no sistema local
                bos.close();

                System.out.println("File received and saved as: " + fileName);  // Confirma que o arquivo foi salvo
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Error receiving file: " + e.getMessage());  // Trata erros ao receber o arquivo
            }
        }
    }
}
