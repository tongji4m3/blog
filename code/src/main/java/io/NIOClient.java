package io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;

public class NIOClient {
    static class NIOClientHandler implements Runnable {
        private final Selector selector;

        public NIOClientHandler(Selector selector) {
            this.selector = selector;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    int readyChannels = selector.select(); // 获取可用channel数量
                    if (readyChannels == 0) {
                        continue;
                    }
                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectionKeys.iterator();
                    while (iterator.hasNext()) {
                        SelectionKey selectionKey = iterator.next();
                        iterator.remove();
                        if (selectionKey.isReadable()) {
                            readHandler(selectionKey, selector);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void readHandler(SelectionKey selectionKey, Selector selector) throws IOException {
            SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
            // 读取服务器端响应数据
            StringBuilder msg = new StringBuilder();
            while (socketChannel.read(byteBuffer) > 0) {
                byteBuffer.flip();
                msg.append(Charset.defaultCharset().decode(byteBuffer));
            }
            // 将channel再次注册到selector上,监听它的可读事件
            socketChannel.register(selector, SelectionKey.OP_READ);
            if (msg.length() > 0) {
                System.out.println(msg.toString());
            }
        }
    }

    public void start() throws IOException {
        SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress("127.0.0.1", 8848));
        // 新开线程，专门接收服务器端发送的信息
        Selector selector = Selector.open();
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ);
        new Thread(new NIOClientHandler(selector)).start();

        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String msg = scanner.nextLine();
            if (msg != null && msg.length() > 0) {
                socketChannel.write(Charset.defaultCharset().encode(msg));
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new NIOClient().start();
    }
}
