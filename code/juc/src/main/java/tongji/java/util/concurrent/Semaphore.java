package tongji.java.util.concurrent;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * 调用的AQS方法，其实只有三种，acquireShared，acquireSharedInterruptibly和tryAcquireSharedNanos
 * 三者区别:
 * 需要响应中断，方法声明会抛出中断异常。
 * 有超时机制，就需要用返回值区别 获得锁返回 和 超时都没获得到锁 两种情况。
 * 这三个方法都需要调用到AQS子类实现的tryAcquireShared，该方法用来获取共享锁，子类可以将其实现公平锁或是非公平锁。
 */
public class Semaphore {
    private final Sync sync;

    abstract static class Sync extends AbstractQueuedSynchronizer {
        Sync(int permits) {
            setState(permits);
        }

        final int getPermits() {
            return getState();
        }

        /**
         * 方法使用了自旋，这是合理且必要的。共享锁是共享的，自然可能有多个线程正在同时执行上面的代码，即使失败了也不能退出循环，而是应该失败后再得到当前值，然后再次CAS尝试。
         * 如果remaining算出来小于0，说明剩余信号量已经不够拿的了，那就直接返回remaining这个负数（表达获取共享锁失败），不做CAS操作。
         * 如果remaining算出来大于等于0，说明剩余信号量够拿的，紧接着如果CAS设置成功，就返回remaining这个大于等于0的数（表达获取共享锁成功）。
         * 这个方法想要退出，只有当前线程拿到了想要数量的信号量，或剩余信号量已经不够拿。
         */
        final int nonfairTryAcquireShared(int acquires) {
            for (; ; ) {
                int available = getState();
                int remaining = available - acquires;
                if (remaining < 0 || compareAndSetState(available, remaining)) {
                    return remaining;
                }
            }
        }

        /**
         * 释放信号量就不用区别什么公平不公平了。
         * 考虑多线程，使用自旋保证releases单位的信号量能够释放到位。
         * 只有CAS设置成功，或溢出int型的范围，才能退出这个循环。
         */
        @Override
        protected boolean tryReleaseShared(int releases) {
            for (; ; ) {
                int current = getState();
                int next = current + releases;
                if (next < current) throw new Error("Maximum permit count exceeded");
                if (compareAndSetState(current,next)) return true;
            }
        }
    }

    static final class FairSync extends Sync {
        FairSync(int permits) {
            super(permits);
        }

        /**
         * 区别就是要先判断同步队列中是否已经有节点了（hasQueuedPredecessors），如果有那同步队列中的节点属于是排在当前线程之前的，所以只好直接返回-1。
         */
        @Override
        protected int tryAcquireShared(int acquires) {
            for (; ; ) {
                if (hasQueuedPredecessors()) return -1;
                int available = getState();
                int remaining = available - acquires;
                if (remaining < 0 || compareAndSetState(available, remaining)) {
                    return remaining;
                }
            }
        }
    }
    static final class NonfairSync extends Sync {
        NonfairSync(int permits) {
            super(permits);
        }

        @Override
        protected int tryAcquireShared(int acquires) {
            return nonfairTryAcquireShared(acquires);
        }
    }

    public Semaphore(int permits) {
        sync = new NonfairSync(permits);
    }

    public Semaphore(int permits, boolean fair) {
        sync = fair ? new FairSync(permits) : new NonfairSync(permits);
    }

    /**
     * 作用就是尝试 一次性的、非公平的 获得锁动作。注意这种一次性动作一定要是非公平实现的，不然大部分情况下（同步队列中只要有一个线程在等待），
     * 这种一次性动作肯定不能成功。这也是为什么要把非公平实现放到NonfairSync和FairSync的父类里的一个公共方法里。
     */
    public boolean tryAcquire() {
        return sync.nonfairTryAcquireShared(1) >= 0;
    }

    public void acquire() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }
    public void release() {
        sync.releaseShared(1);
    }
}
