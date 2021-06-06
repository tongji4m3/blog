package io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class AIOServer {
    public static void main(String[] args) {
        try {
            final int port = 8848;
            AsynchronousServerSocketChannel serverSocketChannel = AsynchronousServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(port));
            // 需要在handler的实现中处理连接请求和监听下一个连接、数据收发，以及通信异常。
            // 消息处理回调接口，是一个负责消费异步IO操作结果的消息处理器
            CompletionHandler<AsynchronousSocketChannel, Object> handler = new CompletionHandler<AsynchronousSocketChannel, Object>() {
                /*
                当I/O操作成功完成时，会回调到completed方法，failed方法则在I/O操作失败时被回调。
                需要注意的是：在CompletionHandler的实现中应当及时处理操作结果，以避免一直占用调用线程而不能分发其他的CompletionHandler处理器。
                 */
                @Override
                public void completed(AsynchronousSocketChannel result, Object attachment) {
                    // 继续监听下一个连接请求
                    serverSocketChannel.accept(attachment, this);
                    try {
                        System.out.println("接受了一个连接：" + result.getRemoteAddress().toString());
                        // result表示当前接受的客户端的连接会话，与客户端的通信都需要通过该连接会话进行。
                        result.write(Charset.defaultCharset().encode("Server:Hello World"));

                        ByteBuffer readBuffer = ByteBuffer.allocate(128);
                        result.read(readBuffer).get();
                        System.out.println(new String(readBuffer.array()));
                    } catch (IOException | InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void failed(Throwable exc, Object attachment) {
                    System.out.println("出错了：" + exc.getMessage());
                }
            };
            // 是一个异步方法，调用会直接返回,为了让子线程能够有时间处理监听客户端的连接会话，这里让主线程休眠一段时间
            serverSocketChannel.accept(null, handler);
            TimeUnit.MINUTES.sleep(Integer.MAX_VALUE);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
