package io;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class BIOServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8848);
        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(() -> handler(socket)).start();
        }
    }

    public static void handler(Socket socket) {
        InputStream inputStream = null;
        try {
            byte[] bytes = new byte[1024];
            inputStream = socket.getInputStream();
            int read;
            while ((read = inputStream.read(bytes)) != -1) {
                System.out.println(new String(bytes, 0, read));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
