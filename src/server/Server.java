package server;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Server extends Application {

    private TextArea logArea;
    private TableView<UserAccount> registeredAccountsTable; 
    private ServerSocket serverSocket;
    private boolean running = false;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        logArea = new TextArea();
        logArea.setEditable(false);

        // Tạo bảng hiển thị tài khoản đã đăng ký
        registeredAccountsTable = new TableView<>();
        TableColumn<UserAccount, String> usernameColumn = new TableColumn<>("Tài Khoản Đã Đăng Ký");
        usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        registeredAccountsTable.getColumns().add(usernameColumn);

        // Các nút điều khiển server
        Button startBtn = new Button("Start");
        Button stopBtn = new Button("Stop");
        Button refreshBtn = new Button("Refresh");

        // Xử lý các sự kiện nút bấm
        startBtn.setOnAction(e -> startServer());
        stopBtn.setOnAction(e -> stopServer());
        refreshBtn.setOnAction(e -> loadAccounts());

        // Tạo layout cho các nút
        HBox buttonLayout = new HBox(10, startBtn, stopBtn, refreshBtn);

        // Sắp xếp giao diện
        BorderPane layout = new BorderPane();
        layout.setTop(buttonLayout);
        layout.setRight(registeredAccountsTable);
        layout.setCenter(logArea);

        registeredAccountsTable.prefWidthProperty().bind(layout.widthProperty().multiply(0.7));

        // Tạo Scene và hiển thị
        Scene scene = new Scene(layout, 600, 400);
        primaryStage.setTitle("Mail Server");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Tải danh sách tài khoản đã đăng ký
        loadAccounts();
    }

    // Hàm để khởi động server
    private void startServer() {
        if (running) {
            logArea.appendText("Server đang chạy.\n");
            return;
        }

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(12345); // Đặt cổng server
                running = true;
                logArea.appendText("Server đã khởi động.\n");

                while (running) {
                    Socket clientSocket = serverSocket.accept(); // Chờ kết nối từ client
                    new ClientHandler(clientSocket, logArea, this).start(); // Khởi động ClientHandler cho từng kết nối
                }
            } catch (IOException e) {
                logArea.appendText("Lỗi khi khởi động server: " + e.getMessage() + "\n");
            }
        }).start();
    }

    // Hàm để dừng server
    private void stopServer() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close(); // Đóng kết nối server
            }
        } catch (IOException e) {
            logArea.appendText("Lỗi khi dừng server: " + e.getMessage() + "\n");
        }
        logArea.appendText("Server đã dừng.\n");
    }

    // Hàm tải danh sách tài khoản đã đăng ký
    private void loadAccounts() {
        File accountsDir = new File("D:\\Code\\Java\\MailServer\\accounts");
        File[] accounts = accountsDir.listFiles(File::isDirectory); // Chỉ lấy thư mục

        if (accounts != null) {
            List<UserAccount> userAccounts = Stream.of(accounts)
                .map(accountDir -> new UserAccount(accountDir.getName()))
                .collect(Collectors.toList());
            registeredAccountsTable.getItems().setAll(userAccounts);
            logArea.appendText("Tải danh sách tài khoản thành công.\n");
        } else {
            logArea.appendText("Không tìm thấy tài khoản nào.\n");
        }
    }
}
