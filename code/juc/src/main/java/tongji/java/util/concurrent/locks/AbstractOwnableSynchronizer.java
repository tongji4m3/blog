package tongji.java.util.concurrent.locks;

public class AbstractOwnableSynchronizer {
    protected AbstractOwnableSynchronizer() {

    }
    // The current owner of exclusive mode synchronization.
    // 用来记录当前持有锁的线程
    private Thread exclusiveOwnerThread;

    protected final void setExclusiveOwnerThread(Thread thread) {
        exclusiveOwnerThread = thread;
    }

    protected final Thread getExclusiveOwnerThread() {
        return exclusiveOwnerThread;
    }
}
