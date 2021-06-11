package tongji.java.util.concurrent;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * 调用的AQS方法，其实只有三种，acquireShared（共享锁的获取与释放中已经进行了讲解），acquireSharedInterruptibly和tryAcquireSharedNanos（CountDownLatch源码解析中已经进行了讲解）。
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

        final int nonfairTryAcquireShared(int acquires) {
            for (; ; ) {
                int available = getState();
                int remaining = available - acquires;
                if (remaining < 0 || compareAndSetState(available, remaining)) {
                    return remaining;
                }
            }
        }

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

    public void acquire() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }
    public void release() {
        sync.releaseShared(1);
    }
}
