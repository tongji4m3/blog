package tongji.java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;


public interface Lock {
    /**
     * If the lock is not available then the current thread becomes disabled for thread scheduling purposes and lies dormant until the lock has been acquired.
     */
    void lock();

    /**
     * If the lock is not available then the current thread becomes disabled for thread scheduling purposes and lies dormant until one of two things happens:
     * The lock is acquired by the current thread; or
     * Some other thread interrupts the current thread
     *
     * If the current thread:
     * has its interrupted status set on entry to this method; or
     * is interrupted while acquiring the lock, and interruption of lock acquisition is supported,
     * then InterruptedException is thrown and the current thread's interrupted status is cleared.
     */
    void lockInterruptibly() throws InterruptedException;

    /**
     * Acquires the lock if it is available and returns immediately with the value true.
     * If the lock is not available then this method will return immediately with the value false.
     */
    boolean tryLock();

    /**
     * Acquires the lock if it is free within the given waiting time and the current thread has not been interrupted.
     * If the lock is available this method returns immediately with the value true.
     * If the lock is not available then the current thread becomes disabled for thread scheduling purposes and lies dormant until one of three things happens:
     * The lock is acquired by the current thread; or
     * Some other thread interrupts the current thread, and interruption of lock acquisition is supported; or
     * The specified waiting time elapses
     *
     * If the current thread:
     * has its interrupted status set on entry to this method; or
     * is interrupted while acquiring the lock, and interruption of lock acquisition is supported,
     * then InterruptedException is thrown and the current thread's interrupted status is cleared.
     *
     * If the specified waiting time elapses then the value false is returned. If the time is less than or equal to zero, the method will not wait at all.
     */
    boolean tryLock(long time, TimeUnit unit) throws InterruptedException;

    void unlock();

    /**
     * Before waiting on the condition the lock must be held by the current thread.
     * A call to Condition.await() will atomically release the lock before waiting and re-acquire the lock before the wait returns.
     */
    Condition newCondition();
}
