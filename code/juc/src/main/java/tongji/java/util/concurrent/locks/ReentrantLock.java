package tongji.java.util.concurrent.locks;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;

/**
 * status:为0时代表资源没有被锁住，为正数时代表被重入的次数
 * A reentrant mutual exclusion Lock
 * <p>
 * The constructor for this class accepts an optional fairness parameter.
 * When set true, under contention, locks favor granting access to the longest-waiting thread.
 * Otherwise this lock does not guarantee any particular access order.
 *
 * 它们的方法就是在设置和改变这个state。而之前说的子类需要实现的方法，简单的说，它的实现逻辑也就是在设置和改变这个state。
 *
 * 当state为0时，代表没有线程持有锁。当state为1时，代表有线程持有锁。当state>1时，代表有线程持有该锁，并且重入过该锁。
 *
 * AQS可以实现 独占锁和共享锁，但ReentrantLock只使用了独占锁部分。获取锁的方式可以分为公平和非公平，响应中断和不响应中断。
 */
public class ReentrantLock implements Lock {
    private final Sync sync;

    // Base of synchronization control for this lock. Subclassed into fair and nonfair versions below.
    abstract static class Sync extends AbstractQueuedSynchronizer {
        // The main reason for subclassing is to allow fast path for nonfair version.
        abstract void lock();

        // Performs non-fair tryLock. tryAcquire is implemented in subclasses, but both need nonfair try for trylock method.

        /**
         * 不管等待队列中是否有等待线程，直接竞争获取state，要是获取成功了，就直接设置当前线程为exclusiveOwnerThread成员了。这不就是，插了所有等待线程的队嘛。
         * 也就是说，非公平锁它可以和队列中的head后继的代表线程同时竞争锁。
         *
         * 但是 非公平锁插队的机会只有在acquire里面第一次执行tryAcquire的时候，一旦这里tryAcquire获取锁失败了，就会进入acquireQueued的死循环，
         * 在循环之前会将当前线程包装成node放到队尾去，之后在循环中的每次循环，想要执行获取锁的动作（tryAcquire(arg)）必须自己是head的后继才可以（p == head）。
         *
         * 非公平锁插队的机会只有，acquire方法里第一次执行tryAcquire的时候，如果这次插队失败，那么它也不可能插队成功了。
         * 公平锁和非公平锁，在进入acquireQueued之后，使用起来没有任何区别。
         */
        final boolean nonfairTryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                if (compareAndSetState(0, acquires)) {
                    // 锁的持有者设置为当前线程
                    setExclusiveOwnerThread(current);
                    return true;
                }
            } else if (current == getExclusiveOwnerThread()) { // 重入
                int nextc = c + acquires;
                if (nextc < 0) throw new Error("Maximum lock count exceeded");
                setState(nextc); // 不用通过自旋设置了，因为已经持有锁
                return true;
            }
            return false;
        }

        protected final boolean tryRelease(int releases) {
            int c = getState() - releases;
            if (Thread.currentThread() != getExclusiveOwnerThread()) throw new IllegalMonitorStateException();
            boolean free = false;
            if (c == 0) {
                free = true;
                setExclusiveOwnerThread(null);
            }
            setState(c); // 不需要CAS
            return free;
        }

        @Override
        protected boolean isHeldExclusively() {
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        final int getHoldCount() {
            return isHeldExclusively() ? getState() : 0;
        }

        final boolean isLocked() {
            return getState() != 0;
        }

        public Thread getOwner() {
            return getState() == 0 ? null : getExclusiveOwnerThread();
        }
    }

    static final class NonfairSync extends Sync {
        @Override
        void lock() {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
            } else {
                acquire(1);
            }
        }

        protected final boolean tryAcquire(int acquires) {
            return nonfairTryAcquire(acquires);
        }
    }

    static final class FairSync extends Sync {

        @Override
        void lock() {
            acquire(1);
        }

        protected final boolean tryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                // 此时CHL队列中可能有线程已经在排队了（考虑到此版本是公平锁），所以需要通过hasQueuedPredecessors判断队列中是否已经有等待线程（返回true代表有）。
                if (!hasQueuedPredecessors() && compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            } else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0) throw new Error("Maximum lock count exceeded");
                // 直接set，是因为当前线程已经获取锁了，此时别的线程都不能修改state，即只有当前线程在写state，所以直接set是安全的。
                setState(nextc);
                return true;
            }
            return false;
        }
    }

    public ReentrantLock() {
        sync = new NonfairSync();
    }

    public ReentrantLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
    }

    /**
     * Acquires the lock if it is not held by another thread and returns immediately, setting the lock hold count to one.
     * If the current thread already holds the lock then the hold count is incremented by one and the method returns immediately.
     * If the lock is held by another thread then the current thread becomes disabled for thread scheduling purposes and lies dormant until the lock has been acquired, at which time the lock hold count is set to one.
     */
    @Override
    public void lock() {
        sync.lock();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        sync.acquireInterruptibly(1);
    }

    /**
     * Acquires the lock if it is not held by another thread and returns immediately with the value true, setting the lock hold count to one.
     * Even when this lock has been set to use a fair ordering policy, a call to tryLock() will immediately acquire the lock if it is available,
     * whether or not other threads are currently waiting for the lock.
     * <p>
     * If the current thread already holds this lock then the hold count is incremented by one and the method returns true.
     * If the lock is held by another thread then this method will return immediately with the value false.
     */
    @Override
    public boolean tryLock() {
        return sync.nonfairTryAcquire(1);
    }

    /**
     * Acquires the lock if it is not held by another thread within the given waiting time and the current thread has not been interrupted.
     * If this lock has been set to use a fair ordering policy then an available lock will not be acquired if any other threads are waiting for the lock. This is in contrast to the tryLock() method.
     * <p>
     * If the lock is held by another thread then the current thread becomes disabled for thread scheduling purposes and lies dormant until one of three things happens:
     * The lock is acquired by the current thread; or
     * Some other thread interrupts the current thread; or
     * The specified waiting time elapses
     */
    @Override
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireNanos(1, unit.toNanos(timeout));
    }

    @Override
    public void unlock() {
        sync.release(1);
    }

    /**
     * If this lock is not held when any of the Condition waiting or signalling methods are called, then an IllegalMonitorStateException is thrown.
     * When the condition waiting methods are called the lock is released and, before they return, the lock is reacquired and the lock hold count restored to what it was when the method was called.
     * If a thread is interrupted while waiting then the wait will terminate, an InterruptedException will be thrown, and the thread's interrupted status will be cleared.
     * Waiting threads are signalled in FIFO order.
     * The ordering of lock reacquisition for threads returning from waiting methods is the same as for threads initially acquiring the lock, which is in the default case not specified, but for fair locks favors those threads that have been waiting the longest.
     */
    @Override
    public Condition newCondition() {
        return sync.newCondition();
    }

    /**
     * class X {
     * ReentrantLock lock = new ReentrantLock();
     * // ...
     * public void m() {
     * assert lock.getHoldCount() == 0;
     * lock.lock();
     * try {
     * // ... method body
     * } finally {
     * lock.unlock();
     * }
     * }
     * }
     */
    public int getHoldCount() {
        return sync.getHoldCount();
    }

    public boolean isHeldByCurrentThread() {
        return sync.isHeldExclusively();
    }

    public boolean isLocked() {
        return sync.isLocked();
    }

    public final boolean isFair() {
        return sync instanceof FairSync;
    }

    protected Thread getOwner() {
        return sync.getOwner();
    }

    /**
     * Queries whether any threads are waiting to acquire this lock. Note that because cancellations may occur at any time, a true return does not guarantee that any other thread will ever acquire this lock. This method is designed primarily for use in monitoring of the system state.
     * Returns:
     * true if there may be other threads waiting to acquire the lock
     */
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     * Queries whether the given thread is waiting to acquire this lock
     */
    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }

    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    /**
     * Queries whether any threads are waiting on the given condition associated with this lock. Note that because timeouts and interrupts may occur at any time, a true return does not guarantee that a future signal will awaken any threads. This method is designed primarily for use in monitoring of the system state.
     */
    public boolean hasWaiters(Condition condition) {
        if (condition == null) throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject)) {
            throw new IllegalArgumentException("not owner");
        }
        return sync.hasWaiters((AbstractQueuedSynchronizer.ConditionObject) condition);
    }

    public int getWaitQueueLength(Condition condition) {
        if (condition == null) throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitQueueLength((AbstractQueuedSynchronizer.ConditionObject) condition);
    }

    protected Collection<Thread> getWaitingThreads(Condition condition) {
        if (condition == null) throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject)condition);
    }
}
