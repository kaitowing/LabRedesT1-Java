import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.Scanner;

public class ChatClient2 {
    private static final String SERVER_ADDRESS = "127.0.0.1";  // Endereço do servidor (localhost)
    private static final int SERVER_PORT = 6924;  // Porta do servidor

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             ObjectOutputStream fileOut = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream fileIn = new ObjectInputStream(socket.getInputStream());
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connected to the server.");
            new Thread(new ReadMessages(in, fileIn)).start();  // Cria uma thread para ler as mensagens do servidor

            String userInput;
            while (true) {
                System.out.print("> ");
                userInput = scanner.nextLine();

                if (userInput.startsWith("/file")) {
                    String[] commandParts = userInput.split(" ", 3);
                    if (commandParts.length < 3) {
                        System.out.println("Usage: /file <username> <filename>");
                    } else {
                        String fileName = commandParts[2];
                        File file = new File(fileName);

                        if (file.exists()){
                            out.println(userInput);  // Envia o comando para o servidor
                            sendFile(file, fileName, fileOut);
                        } else {
                            System.out.println("File not found: " + fileName);
                        }
                    }
                } else {
                    out.println(userInput);  // Envia outros comandos para o servidor
                }

                if (userInput.equals("/quit")) {
                    break;  // Encerra o cliente ao digitar /quit
                }
            }
        } catch (IOException e) {
            System.out.println("Error connecting to the server: " + e.getMessage());
        }
    }

    // Método para enviar arquivo
    private static boolean sendFile(File file, String fileName, ObjectOutputStream fileOut) {
        try {
            byte[] byteArray = new byte[(int) file.length()];
            FileInputStream fis = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(fis);
            bis.read(byteArray, 0, byteArray.length);
            bis.close();

            System.out.println("Sending file: " + fileName);
            fileOut.writeObject(fileName);  // Envia o nome do arquivo
            fileOut.writeObject(byteArray);  // Envia o conteúdo do arquivo
            fileOut.flush();
            System.out.println("File sent.");
        } catch (IOException e) {
            System.out.println("Error sending file: " + e.getMessage());
            return false;
        }
        return true;
    }

    // Thread para ler mensagens do servidor
    private static class ReadMessages implements Runnable {
        private BufferedReader in;
        private ObjectInputStream fileIn;

        public ReadMessages(BufferedReader in, ObjectInputStream fileIn) {
            this.in = in;
            this.fileIn = fileIn;
        }

        public void run() {
            try {
                String messageFromServer;
                String fileName = "";
                while ((messageFromServer = in.readLine()) != null) {
                    if (messageFromServer.startsWith("FILE INCOMING")) {
                        // Chamar método para receber o arquivo
                        receiveFile(fileIn, messageFromServer.split(" ")[3]);
                    } else {
                        System.out.println(messageFromServer);  // Exibe mensagens recebidas do servidor
                    }
                }
            } catch (IOException e) {
                System.out.println("Error reading from server: " + e.getMessage());
            }
        }

        // Método para receber arquivo
        private void receiveFile(ObjectInputStream fileIn, String sender) {
            try {
                // Recebe o nome do arquivo e o conteúdo do arquivo como um byte array
                String fileName = (String) fileIn.readObject();
                byte[] fileBytes = (byte[]) fileIn.readObject();

                // Salva o arquivo no sistema local
                fileName = "src/" + sender + "_" + LocalDateTime.now().toString().replace(":", "-") + ".txt";
                FileOutputStream fos = new FileOutputStream(fileName);
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                bos.write(fileBytes);
                bos.close();

                System.out.println("File received and saved as: " + fileName);
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Error receiving file: " + e.getMessage());
            }
        }
    }
}
