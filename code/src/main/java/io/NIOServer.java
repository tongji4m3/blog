package io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;

public class NIOServer {
    public void start() throws IOException {
        Selector selector = Selector.open();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(8848));
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
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
                } else if (selectionKey.isAcceptable()) {
                    acceptHandler(serverSocketChannel, selector);
                }
            }
        }
    }

    private void acceptHandler(ServerSocketChannel serverSocketChannel, Selector selector) throws IOException {
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ);
        // encode返回的是ByteBuffer
        socketChannel.write(Charset.defaultCharset().encode("成功连上了服务器!"));
    }

    private void readHandler(SelectionKey selectionKey, Selector selector) throws IOException {
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        // 读取客户端数据
        StringBuilder msg = new StringBuilder();
        while (socketChannel.read(byteBuffer) > 0) {
            byteBuffer.flip();
            msg.append(Charset.defaultCharset().decode(byteBuffer));
        }
        // 将channel再次注册到selector上,监听它的可读事件
        socketChannel.register(selector, SelectionKey.OP_READ);
        if (msg.length() > 0) {
            System.out.println(msg.toString());
            broadCast(selector, socketChannel, msg.toString());
        }
    }

    private void broadCast(Selector selector, SocketChannel sourceChannel, String msg) {
        // 获取所有已接入客户端channel
        Set<SelectionKey> selectionKeys = selector.keys();
        selectionKeys.forEach(selectionKey -> {
            Channel targetChannel = selectionKey.channel();
            // 剔除发消息的那个channel
            if (targetChannel instanceof SocketChannel && targetChannel != sourceChannel) {
                try {
                    // 向所有channel广播信息
                    ((SocketChannel) targetChannel).write(Charset.defaultCharset().encode(msg));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void main(String[] args) throws IOException {
        new NIOServer().start();
    }
}
