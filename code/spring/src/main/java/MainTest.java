import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MainTest {
    static final Lock lock = new ReentrantLock();
    static final Condition condition = lock.newCondition();

    static class Consumer implements Runnable {
        @Override
        public void run() {
            consumer();
        }

        private void consumer() {
            try{
                lock.lock();
                System.out.println("Consumer获取锁");
                condition.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                System.out.println("Consumer获取condition");
                lock.unlock();
            }
        }
    }

    static class Producer implements Runnable {
        @Override
        public void run() {
            produce();
        }

        private void produce() {
            try{
                lock.lock();
                System.out.println("Producer获取锁");
                condition.signal();
                System.out.println("Producer释放condition");
            }finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) {
        new Thread(new Consumer()).start();
        new Thread(new Producer()).start();
        new ReentrantLock();
    }

}
