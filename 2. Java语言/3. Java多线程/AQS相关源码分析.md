# ReentrantLock

## 构造函数

可以选择构造公平锁或非公平锁

```java
private final Sync sync;

public ReentrantLock() {
	sync = new NonfairSync();
}
public ReentrantLock(boolean fair) {
	sync = fair ? new FairSync() : new NonfairSync();
}
```

## Lock()

![20216121](https://tongji2021.oss-cn-shanghai.aliyuncs.com/img/20216121.jpg)

### AQS::acquire(1)

```java
public final void acquire(int arg) {
    if (!tryAcquire(arg) && acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
        selfInterrupt();
}
```

1. 首先调用了子类实现的**tryAcquire()**方法，如果锁获取成功，则退出函数
2. 获取不成功则调用**addWaiter(Node.EXCLUSIVE)**用一个Node封装该线程并且加入等待队列队尾
3. 接着调用**acquireQueued(final Node node, int arg)**对刚刚生成的Node进行一系列的操作，该Node可能会经历多次阻塞/唤醒，直到最终成功获取锁
4. **selfInterrupt()**主要用于在某些情况下恢复中断状态

当然只是简单的概括，因为这里涉及到AQS的核心，可以看之后关于AQS的源码分析

### FairSync::tryAcquire(1)

```java
protected final boolean tryAcquire(int acquires) {
    final Thread current = Thread.currentThread();
    int c = getState();
    if (c == 0) {
        if (!hasQueuedPredecessors() && compareAndSetState(0, acquires)) {
            setExclusiveOwnerThread(current);
            return true;
        }
    }
    else if (current == getExclusiveOwnerThread()) {
        int nextc = c + acquires;
        if (nextc < 0) throw new Error("Maximum lock count exceeded");
        setState(nextc);
        return true;
    }
    return false;
}
```

该方法**只尝试获取锁一次**

1. 首先获取state，因为state用volatile修饰，所以能获取内存最新值
2. 若state为0，则通过`hasQueuedPredecessors()`查询是否有任何线程等待获取的时间比当前线程长（**实现公平**）。如果没有，则用CAS交换state来实现获取资源逻辑，如果CAS成功，则设置本线程为设置当前拥有独占访问权限的线程，成功获取锁。
3. state不为0，则**查看当前线程是不是独占锁的那个线程**，是则调用setState（该方法无同步操作，因为是获取独占锁的线程操作的，无竞争）将重入次数+1。成功获取锁
4. 不符合则返回false，没有获取到锁

### 源码

![image-20210612112651838](https://tongji2021.oss-cn-shanghai.aliyuncs.com/img/image-20210612112651838.png)

```java
public void lock() {
    sync.lock();
}

private final Sync sync;

abstract static class Sync extends AbstractQueuedSynchronizer {
    abstract void lock();
    
    final boolean nonfairTryAcquire(int acquires) {
        final Thread current = Thread.currentThread();
        int c = getState();
        if (c == 0) {
            if (compareAndSetState(0, acquires)) {
                setExclusiveOwnerThread(current);
                return true;
            }
        }
        else if (current == getExclusiveOwnerThread()) {
            int nextc = c + acquires;
            if (nextc < 0) // overflow
                throw new Error("Maximum lock count exceeded");
            setState(nextc);
            return true;
        }
        return false;
    }
}

// 公平锁实现
static final class FairSync extends Sync {
    final void lock() {
        acquire(1);
    }
    
    protected final boolean tryAcquire(int acquires) {
        final Thread current = Thread.currentThread();
        int c = getState();
        if (c == 0) {
            if (!hasQueuedPredecessors() && compareAndSetState(0, acquires)) {
                setExclusiveOwnerThread(current);
                return true;
            }
        }
        else if (current == getExclusiveOwnerThread()) {
            int nextc = c + acquires;
            if (nextc < 0) throw new Error("Maximum lock count exceeded");
            setState(nextc);
            return true;
        }
        return false;
    }
}

// 非公平锁实现
static final class NonfairSync extends Sync {
    final void lock() {
        if (compareAndSetState(0, 1))
            setExclusiveOwnerThread(Thread.currentThread());
        else
            acquire(1);
    }
    
    protected final boolean tryAcquire(int acquires) {
        return nonfairTryAcquire(acquires);
    }
}



// AQS源码
public final void acquire(int arg) {
    if (!tryAcquire(arg) && acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
        selfInterrupt();
}

protected boolean tryAcquire(int arg) {
    throw new UnsupportedOperationException();
}
```

## UnLock()

<img src="https://tongji2021.oss-cn-shanghai.aliyuncs.com/img/image-20210612114348805.png" alt="image-20210612114348805" style="zoom:67%;" />

### AQS::release(1)

```java
public final boolean release(int arg) {
    if (tryRelease(arg)) {
        Node h = head;
        if (h != null && h.waitStatus != 0) unparkSuccessor(h);
        return true;
    }
    return false;
}
```

1. 还是先调用子类的**tryRelease**方法尝试释放资源，释放不成功则返回false（可能仍然是重入状态）
2. 如果释放成功，则唤醒等待队列的后续节点（如果存在的话）

详细分析请看对AQS的分析！

### AQS::tryRelease(1)

```java
protected final boolean tryRelease(int releases) {
    int c = getState() - releases;
    if (Thread.currentThread() != getExclusiveOwnerThread())
        throw new IllegalMonitorStateException();
    boolean free = false;
    if (c == 0) {
        free = true;
        setExclusiveOwnerThread(null);
    }
    setState(c);
    return free;
}
```

1. 如果本线程不是独占锁的线程，则抛出异常
2. 如果释放state后state为0，则说明完全释放锁了，设置该锁的独占线程为null，设置state，返回释放成功
3. 否则说明还是重入的状态，设置state状态（减少一次重入），则返回释放锁失败

### 源码

```java
public void unlock() {
	sync.release(1);
}

// AQS
public final boolean release(int arg) {
    if (tryRelease(arg)) {
        Node h = head;
        if (h != null && h.waitStatus != 0) unparkSuccessor(h);
        return true;
    }
    return false;
}

protected boolean tryRelease(int arg) {
    throw new UnsupportedOperationException();
}

// Sync
protected final boolean tryRelease(int releases) {
    int c = getState() - releases;
    if (Thread.currentThread() != getExclusiveOwnerThread())
        throw new IllegalMonitorStateException();
    boolean free = false;
    if (c == 0) {
        free = true;
        setExclusiveOwnerThread(null);
    }
    setState(c);
    return free;
}
```

