# BIO

## 概述

blocking I/O。同步阻塞模型

由一个独立的Acceptor线程负责监听客户端的连接，它接收到客户端连接请求之后为每个客户端创建一个新的线程进行处理。

每个线程都需要创建独立的线程，当并发量大时，需要创建大量线程来处理连接，系统资源占用大

代码中的read操作是阻塞操作，如果连接之后，服务端一直不发送数据，将会一直阻塞当前线程，浪费资源。

<img src="https://tongji2021.oss-cn-shanghai.aliyuncs.com/img/image-20210603081447596.png" alt="image-20210603081447596" style="zoom: 67%;" />

## 详解

当服务器进程运行时, 可能会同时监听到多个客户的连接请求。

每当一个客户进程执行以下代码：

```java
Socket client = new Socket("127.0.0.1", 8848);
```

就意味着在远程主机的 8848 端口上, 监听到了一个客户的连接请求。管理客户连接请求的任务是由操作系统来完成的。操作系统把这些连接请求存储在一个先进先出的队列中。当队列中的连接请求达到了队列的最大容量时, 服务器进程所在的主机会拒绝新的连接请求。只有当服务器进程通过 ServerSocket 的 accept() 方法从队列中取出连接请求, 使队列腾出空位时，队列才能继续加入新的连接请求。

 对于客户进程, 如果它发出的连接请求被加入到服务器的请求连接队列中, 就意味着客户与服务器的连接建立成功, 客户进程从 Socket 构造方法中正常返回。

当客户进程的 Socket构造方法返回成功, 表示客户进程的连接请求被加入到服务器进程的请求连接队列中。 虽然客户端成功返回 Socket对象， 但是还没跟服务器进程形成一条通信线路。必须在服务器进程通过 ServerSocket 的 accept() 方法从请求连接队列中取出连接请求，并返回一个Socket 对象后，服务器进程这个Socket 对象才与客户端的 Socket 对象形成一条通信线路。

ServerSocket 的 accept() 方法从连接请求队列中取出一个客户的连接请求，然后创建与客户连接的 Socket 对象, 并将它返回。如果队列中没有连接请求，accept() 方法就会一直等待，直到接收到了连接请求才返回。

 接下来，服务器从 Socket 对象中获得输入流和输出流，就能与客户交换数据。

服务器的主线程负责接收客户的连接, 每次接收到一个客户连接, 就会创建一个工作线程, 由它负责与客户的通信

## 示例程序

**Server**

```java
public class BIOServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8848);
        while (true) {
            // 线程在accept调用时处于休眠状态，等待某个客户连接到达并被内核接受
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
```

**Client**

```java
public class BIOClient {
    public static void main(String[] args) throws IOException {
        Socket client = new Socket("127.0.0.1", 8848);
        OutputStream outputStream = client.getOutputStream();
        String msg = "Hello Server!";
        outputStream.write(msg.getBytes(StandardCharsets.UTF_8));
        client.close();
    }
}
```

# NIO

non-blocking I/O或New IO

同步非阻塞，服务端可以开启一个线程处理多个连接，它是非阻塞的，客户端发送的数据都会注册到多路复用器selector上面，当selector（selector的select方法是阻塞的）轮询到有读、写或者连接请求时，才会转发到后端程序进行处理，没有数据的时候，业务程序并不需要阻塞等待。

![image-20210603090056517](https://tongji2021.oss-cn-shanghai.aliyuncs.com/img/image-20210603090056517.png)

## Channel

+ 类比于IO流，但是具有双向性，既可读，又可写
+ ServerSocketChannel用来监听客户端连接请求并创建SocketChannel与客户端进行通信
+ 只能通过Buffer读写Channel中数据

## Buffer

本质上是一块内存区域

**字段**

```java
private int mark = -1; // 存储特定position位置，后续通过reset恢复到该位置，依然可以读取这里的数据
private int position = 0; // 读写操作时的索引下标，最大为capacity-1;写模式切换到读模式时，会变为0
private int limit; // 写模式下等于capacity，切换到读模式时，limit表示最多能从buffer中读取多少，等于写模式下的position
private int capacity; // 容量，标识最大能容纳多少字节
```

**API实例**

```java
ByteBuffer byteBuffer = ByteBuffer.allocate(10); // position = 0,limit = 10,capacity=10
byteBuffer.put("aaa".getBytes()); // position = 3, limit = 10, capacity = 10
byteBuffer.flip(); // 从写模式切换到读模式 position = 0, limit = 3, capacity = 10
byteBuffer.get();  // position = 1, limit = 3, capacity = 10
byteBuffer.mark(); // mark = 1, position = 1, limit = 3, capacity = 10
byteBuffer.get();  // mark = 1, position = 2, limit = 3, capacity = 10
byteBuffer.reset();  // mark = 1, position = 1, limit = 3, capacity = 10
byteBuffer.clear(); // 所有属性重置 position = 0, limit = 10, capacity = 10
```

## Selector

**选择器** /**多路复用器**。用于检查一个或多个NIO Channel（通道）的状态是否处于可读、可写。如此可以实现单线程管理多个channels，也就是可以管理多个网络连接。

## 示例程序

**NIOServer**

```java
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

    private void acceptHandler(ServerSocketChannel serverSocketChannel, 
                               Selector selector) throws IOException {
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ);
        // encode返回的是ByteBuffer
        socketChannel.write(Charset.defaultCharset().encode("成功连上了服务器!"));
    }

    private void readHandler(SelectionKey selectionKey, 
                             Selector selector) throws IOException {
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
            if (targetChannel instanceof SocketChannel 
                && targetChannel != sourceChannel) {
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
```

**NIOClient**

```java
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

        private void readHandler(SelectionKey selectionKey, 
                                 Selector selector) throws IOException {
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
        SocketChannel socketChannel = 
            SocketChannel.open(new InetSocketAddress("127.0.0.1", 8848));
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
```



# AIO

Asynchronous I/O或NIO.2

异步非阻塞，服务器实现模式为一个有效请求一个线程，客户端的I/O请求都是由OS先完成了再通知服务器应用去启动线程进行处理，

# Netty