import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.util.Scanner;

public class UDPChatClient3 {
    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int SERVER_PORT = 6924;
    private static final int BUFFER_SIZE = 4094;

    public static void main(String[] args) {
        try (DatagramSocket clientSocket = new DatagramSocket();
                Scanner scanner = new Scanner(System.in)) {

            InetAddress serverAddress = InetAddress.getByName(SERVER_ADDRESS);

            System.out.println("Connected to the server.");

            Thread messageReceiver = new Thread(new MessageReceiver(clientSocket));
            messageReceiver.start();

            while (true) {
                System.out.print("> ");
                String userInput = scanner.nextLine();

                if (userInput.startsWith("/file")) {
                    String[] commandParts = userInput.split(" ", 3);
                    if (commandParts.length < 3) {
                        System.out.println("Usage: /file <username> <filepath>");
                    } else {
                        String fileName = commandParts[2];
                        File file = new File(fileName);

                        if (file.exists()) {
                            sendCommand(clientSocket, userInput, serverAddress);
                            sendFile(file, fileName, clientSocket, serverAddress);
                        } else {
                            System.out.println("File not found: " + fileName);
                        }
                    }
                } else {
                    sendCommand(clientSocket, userInput, serverAddress);
                }

                if (userInput.equals("/quit")) {
                    break;
                }
            }

            messageReceiver.interrupt();

        } catch (IOException e) {
            System.out.println("Error connecting to the server: " + e.getMessage());
        }
    }

    private static void sendCommand(DatagramSocket socket, String command, InetAddress serverAddress)
            throws IOException {
        byte[] sendBuffer = command.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, serverAddress, SERVER_PORT);
        socket.send(sendPacket);
    }

    private static void sendFile(File file, String fileName, DatagramSocket socket, InetAddress serverAddress)
            throws IOException {
        byte[] fileBytes = new byte[(int) file.length()];
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis);
        bis.read(fileBytes, 0, fileBytes.length);
        
        DatagramPacket filePacket = new DatagramPacket(fileBytes, fileBytes.length, serverAddress, SERVER_PORT);
        socket.send(filePacket);
        bis.close();
    }

    static class MessageReceiver implements Runnable {
        private DatagramSocket socket;
        private byte[] receiveBuffer = new byte[BUFFER_SIZE];

        public MessageReceiver(DatagramSocket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                    socket.receive(receivePacket);
                    String response = new String(receivePacket.getData(), 0, receivePacket.getLength());

                    if (response.startsWith("FILE INCOMING")) {
                        receiveFile(socket, response.split(" ")[3]);
                    } else {
                        System.out.println(response);
                    }
                }
            } catch (IOException e) {
                System.out.println("Error receiving message from server: " + e.getMessage());
            }
        }

        private void receiveFile(DatagramSocket socket, String sender) throws IOException {
            byte[] fileBuffer = new byte[BUFFER_SIZE];
            DatagramPacket filePacket = new DatagramPacket(fileBuffer, fileBuffer.length);
            socket.receive(filePacket);

            String fileName = "src/received_" + LocalDateTime.now().toString().replace(":", "-") + ".txt";
            FileOutputStream fos = new FileOutputStream(fileName);
            fos.write(filePacket.getData(), 0, filePacket.getLength());
            fos.close();

            System.out.println("File received from " + sender + " and saved as: " + fileName);
        }
    }
}
