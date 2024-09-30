package server;

import javafx.scene.control.TextArea;
import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler extends Thread {
    private Socket clientSocket;
    private TextArea logArea;
    private PrintWriter out;
    private Server server;
    private static Map<String, Integer> activeUsers = new ConcurrentHashMap<>(); // Theo dõi người dùng đang truy cập
    private int udpPort;

    public ClientHandler(Socket socket, TextArea logArea, Server server) {
        this.clientSocket = socket;
        this.logArea = logArea;
        this.server = server;
        this.udpPort = 12346; 
    }

    @Override
    public void run() {
        new Thread(this::handleEmailUDP).start();
        
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            
            String request;
            while ((request = in.readLine()) != null) {
                if (request.startsWith("REGISTER")) {
                    handleRegister(request);
                } else if (request.startsWith("LOGIN")) {
                    handleLogin(request);
                }else if (request.startsWith("INBOX_REQUEST")) {
                    String username = request.split("\\|")[1];
                    handleInboxRequest(username);  // Xử lý yêu cầu hộp thư đến
                }
            }
        } catch (IOException e) {
            logArea.appendText("Error handling client: " + e.getMessage() + "\n");
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                logArea.appendText("Error closing client socket: " + e.getMessage() + "\n");
            }
        }
    }
    // Phương thức xử lý yêu cầu hộp thư đến
    private void handleInboxRequest(String username) {
        File userDir = new File("D:\\Code\\Java\\MailServer\\accounts\\" + username);
        File[] emailFiles = userDir.listFiles((dir, name) -> name.endsWith(".txt"));
        
        if (emailFiles != null) {
            try (DatagramSocket udpSocket = new DatagramSocket()) {
                InetAddress clientAddress = InetAddress.getByName(clientSocket.getInetAddress().getHostAddress());
                for (File emailFile : emailFiles) {
                    StringBuilder emailContent = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new FileReader(emailFile))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            emailContent.append(line).append("\n");
                        }
                    }
                    // Gửi nội dung email qua UDP
                    byte[] buffer = emailContent.toString().getBytes();
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, clientAddress, udpPort);
                    udpSocket.send(packet);
                }
            } catch (IOException e) {
                logArea.appendText("Lỗi khi gửi email qua UDP: " + e.getMessage() + "\n");
            }
        } else {
            logArea.appendText("Không tìm thấy email cho người dùng: " + username + "\n");
        }
    }


    private void handleRegister(String request) {
        String[] parts = request.split(" ");
        String username = parts[1];
        String password = parts[2];

        File accountDir = new File("D:\\Code\\Java\\MailServer\\accounts\\" + username);

        if (accountDir.exists()) {
            sendResponseToClient("ERROR: Tài khoản đã tồn tại.");
            return;
        }

        if (!accountDir.mkdir()) {
            sendResponseToClient("ERROR: Không thể tạo thư mục tài khoản.");
            return;
        }

        File credentialsFile = new File(accountDir, "credentials.txt");
        try (PrintWriter writer = new PrintWriter(credentialsFile)) {
            writer.println("Username: " + username);
            writer.println("Password: " + password);
            sendResponseToClient("SUCCESS: Tài khoản đã được tạo thành công.");
        } catch (IOException e) {
            sendResponseToClient("ERROR: Không thể lưu thông tin đăng nhập.");
        }
    }

    private void handleLogin(String request) {
        String[] parts = request.split(" ");
        String username = parts[1];
        String password = parts[2];

        File accountFile = new File("D:\\Code\\Java\\MailServer\\accounts\\" + username + "\\credentials.txt");
        if (!accountFile.exists()) {
            sendResponseToClient("ERROR: Người dùng không tồn tại.");
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(accountFile))) {
            String savedUsername = br.readLine().split(": ")[1];
            String savedPassword = br.readLine().split(": ")[1];
            if (savedUsername.equals(username) && savedPassword.equals(password)) {
                logArea.appendText("Người dùng đã đăng nhập: " + username + "\n");
                sendResponseToClient("SUCCESS: Đăng nhập thành công.");
                
                // Thêm vào danh sách người dùng đang truy cập
                activeUsers.put(username, udpPort);

                // Gửi danh sách email cho người dùng
            } else {
                sendResponseToClient("ERROR: Thông tin đăng nhập không hợp lệ.");
            }
        } catch (IOException e) {
            logArea.appendText("Lỗi khi đọc thông tin người dùng: " + e.getMessage() + "\n");
        }
    }

    private void sendResponseToClient(String response) {
        if (out != null) {
            out.println(response);
            logArea.appendText("SERVER SEND: " + response + "\n");
        }
    }

    public void handleEmailUDP() {
        try (DatagramSocket udpSocket = new DatagramSocket(12345)) {
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            while (true) {
                udpSocket.receive(packet);
                String emailData = new String(packet.getData(), 0, packet.getLength());
                String[] emailParts = emailData.split("\\|");

                if (emailParts.length < 4) continue; 
                String sender = emailParts[0];
                String recipient = emailParts[1];
                String subject = emailParts[2];
                String content = emailParts[3];

                logArea.appendText("Email nhận được từ: " + sender + " cho: " + recipient + " với tiêu đề: " + subject + "\n");
                saveEmailToFile(sender, recipient, subject, content);
            }
        } catch (IOException e) {
            logArea.appendText("Lỗi khi nhận email qua UDP: " + e.getMessage() + "\n");
        }
    }

    private void saveEmailToFile(String sender, String recipient, String subject, String content) {
        File recipientDir = new File("D:\\Code\\Java\\MailServer\\accounts\\" + recipient);
       
        File emailFile = new File(recipientDir, subject + ".txt");
        try (PrintWriter writer = new PrintWriter(emailFile)) {
            writer.println("Gửi từ: " + sender);
            writer.println("Tiêu đề: " + subject);
            writer.println("Nội dung: " + content);
            logArea.appendText("Email đã được lưu cho: " + recipient + " với tiêu đề: " + subject + "\n");
        } catch (IOException e) {
            logArea.appendText("Lỗi khi lưu email: " + e.getMessage() + "\n");
        }
    }
    
    

    
   
}
