package io;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class BIOClient {
    public static void main(String[] args) throws IOException {
        Socket client = new Socket("127.0.0.1", 8848);
        OutputStream outputStream = client.getOutputStream();
        String msg = "Hello Server!";
        outputStream.write(msg.getBytes(StandardCharsets.UTF_8));
        client.close();
    }
}
