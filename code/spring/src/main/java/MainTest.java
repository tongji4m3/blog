import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;

public class MainTest {
    public static void main(String[] args) throws InterruptedException {
        Thread thread = new Thread(() -> {
            System.out.println("线程开始执行");
            LockSupport.park();
            System.out.println("线程执行结束");
        });
        thread.start();
        TimeUnit.SECONDS.sleep(3);
        System.out.println("执行unpark");
        LockSupport.unpark(thread);

        AbstractQueuedSynchronizer
    }
}
