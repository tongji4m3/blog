秋招成神之路——Java并发编程专题之LockSupport

> 后续我会发布一系列和并发编程相关的博客，不会太深，也不会太浅，既触碰到源码原理，又不会迷失于细节之中。主要选取的也是面试高频考点，但是我不想只停留在API表面，或者仅仅是收集一些面试题来背，而是会尽力搞清楚他们的原理，让大家能知其然，知其所以然，牢牢记住，构建自己的知识体系。
> 能力有限，如有错误，希望能批评指正
> 希望博客能伴我成长，也欢迎小伙伴加我微信：15316162191，大家一起备战秋招
> 点个关注不迷路~

# LockSupport

## 概述

LockSupport是一个编程工具类，主要是为了阻塞和唤醒线程。它的所有方法都是静态方法，它可以让线程在任意位置阻塞，也可以在任意位置唤醒。

它可以在阻塞线程时为线程设置一个blocker，这个blocker是用来记录线程被阻塞时被谁阻塞的，用于线程监控和分析工具来定位原因。

LockSupport类与每个使用它的线程都会关联一个许可证，在默认情况下调用LockSupport类的方法的线程是不持有许可证的。

```java
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
}
```

**和wait/notify区别**

1. wait和notify都必须先获得锁对象才能调用，但是park不需要获取某个对象的锁就可以锁住线程。
2. notify只能随机选择一个线程唤醒，无法唤醒指定的线程，unpark却可以唤醒一个指定的线程。

## 重要方法

这些方法都是调用Unsafe类的native方法

```java
private static final sun.misc.Unsafe UNSAFE;

public final class Unsafe {
    public native void park(boolean isAbsolute, long time);
    public native void unpark(Thread jthread);
}
```

### park(Object blocker)

**setBlocker**记录了当前线程是被blocker阻塞的，当线程在没有持有许可证的情况下调用park方法而被阻塞挂起时，这个blocker对象会被记录到该线程内部。使用诊断工具可以观察线程被阻塞的原因，诊断工具是通过调用getBlocker(Thread)方法来获取blocker对象的，所以推荐使用`LockSupport.park(this);`

如果调用park方法的线程已经拿到了与LockSupport关联的许可证，则调用LockSupport.park()时会马上返回，否则调用线程会被阻塞挂起。在其他线程调用unpark(Thread thread) 方法并且将当前线程作为参数时，调用park方法而被阻塞的线程会返回。另外，如果其他线程调用了阻塞线程的interrupt()方法，设置了中断标志或者被虚假唤醒，则阻塞线程也会返回。

当调用interrupt方法时，会把中断状态设置为true，然后park方法会去判断中断状态，如果为true，就直接返回，然后往下继续执行，并不会抛出异常。注意，这里并不会清除中断标志。

**线程如果因为调用park而阻塞的话，能够响应中断请求(中断状态被设置成true)，但是不会抛出InterruptedException**。

所以park之后有两种方式让线程可以继续运行：

+ **LockSupport.unpark(thread)**
+ **thread.interrupt()**

```java
public static void park(Object blocker) {
    Thread t = Thread.currentThread();
    setBlocker(t, blocker);
    UNSAFE.park(false, 0L);
    setBlocker(t, null); // 线程被激活后清除blocker变量
}
private static void setBlocker(Thread t, Object arg) {
    UNSAFE.putObject(t, parkBlockerOffset, arg);
}
```

### unpark(Thread thread)

如果thread之前因调用park()而被挂起，则调用unpark后，该线程会被唤醒。

如果thread之前没有调用park，则让thread持有一个许可证，之后再调用park方法，则会立即返回。

```java
public static void unpark(Thread thread) {
    if (thread != null) UNSAFE.unpark(thread);
}
```

### parkNanos(Object blocker, long nanos)

如果没有拿到许可证，则阻塞当前线程，最长不超过nanos纳秒，返回条件在park()的基础上增加了超时返回

```java
public static void parkNanos(Object blocker, long nanos) {
    if (nanos > 0) {
        Thread t = Thread.currentThread();
        setBlocker(t, blocker);
        UNSAFE.park(false, nanos);
        setBlocker(t, null);
    }
}
```

### parkUntil

阻塞当前线程，直到deadline；

```java
public static void parkUntil(Object blocker, long deadline) {
    Thread t = Thread.currentThread();
    setBlocker(t, blocker);
    UNSAFE.park(true, deadline);
    setBlocker(t, null);
}
```

## 原理浅析

> 只讲部分重点，非重点代码略去了

### 概述

每个java线程都有一个Parker实例，Parker类定义：

```java
class Parker {
private:
  volatile int _counter; // 记录许可
public:
  void park(bool isAbsolute, jlong time);
  void unpark();
}
```

LockSupport通过控制_counter进行线程的阻塞/唤醒，原理类似于信号量机制的PV操作，其中Semaphore初始为0，最多为1。

形象的理解，线程阻塞需要消耗凭证(permit)，这个凭证最多只有1个。当调用park方法时，如果有凭证，则会直接消耗掉这个凭证然后正常退出；但是如果没有凭证，就必须阻塞等待凭证可用；而unpark则相反，它会增加一个凭证，但凭证最多只能有1个。

`_counter`只能在0和1之间取值：当为1时，代表该类被unpark调用过，更多的调用，也不会增加`_counter`的值，当该线程调用park()时，不会阻塞，同时_counter立刻清零。当为0时, 调用park()会被阻塞。

- 为什么可以先唤醒线程后阻塞线程？
    因为unpark获得了一个凭证，之后调用park因为有凭证消费，故不会阻塞。
- 为什么唤醒两次后阻塞两次会阻塞线程。
    因为凭证的数量最多为1，连续调用两次unpark和调用一次unpark效果一样，只会增加一个凭证；而调用两次park却需要消费两个凭证。

### park()

![未命名文件](https://tongji2021.oss-cn-shanghai.aliyuncs.com/img/未命名文件.jpg)

- 检查`_counter`是否大于零(之前**调用过unpark**)，则通过**原子操作将_counter设置为0**。线程不用阻塞并返回。
- 检查该线程是否有**中断信号**，如果有则清除该中断信号并返回（不抛出异常）。
- 尝试通过`pthread_mutex_trylock`对_mutex**加锁**来达到线程互斥。
- 检查park是否设置超时时间， 若设置了通过safe_cond_timedwait进行**超时等待**； 若没有设置，调用pthread_cond_wait进行**阻塞等待**。 这两个函数都在阻塞等待时都会放弃cpu的使用。 **直到别的线程去唤醒它**（调用pthread_cond_signal）。safe_cond_timedwait/pthread_cond_wait在执行之前肯定已经获取了锁_mutex, 在睡眠前释放了锁, 在被唤醒之前, 首先再去获取锁。
- **将_counter设置为零**。
- 通过pthread_mutex_unlock**释放锁**。

```java
void Parker::park(bool isAbsolute, jlong time) {
  if (Atomic::xchg(0, &_counter) > 0) return; // 调用过unpark
    
  if (Thread::is_interrupted(thread, false)) return; // 中断过
  
  // 对_mutex加锁
  if (Thread::is_interrupted(thread, false) || pthread_mutex_trylock(_mutex) != 0) {
    	return;
  }
 
  // 进行超时等待或者阻塞等待，直到被signal唤醒
  if (time == 0) {
    status = pthread_cond_wait (&_cond[_cur_index], _mutex); 
  } else {
    status = os::Linux::safe_cond_timedwait (&_cond[_cur_index], _mutex, &absTime);
  }
  _counter = 0; // 唤醒后消耗掉这个凭证
  status = pthread_mutex_unlock(_mutex); // 解锁
}
```

### unpark()

- 首先获取锁_mutex。
- 不管之前是什么值，都**将_counter置为1**，所以无论多少函数调用unpark()，都是无效的，只会记录一次。
- 检查线程是否已经被阻塞了，阻塞则**调用pthread_cond_signal唤醒**。
- 最后释放锁_mutex。

```java
void Parker::unpark() {
  status = pthread_mutex_lock(_mutex);   
  s = _counter;
  _counter = 1; // 将_counter置1
  if (s < 1)  status = pthread_cond_signal (&_cond[_cur_index]); // 进行线程唤醒
  pthread_mutex_unlock(_mutex);
```

# ReentrantLock

## 构造函数

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

### Sync::tryRelease(1)

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

## tryLock()

```java
public boolean tryLock() {
	return sync.nonfairTryAcquire(1);
}

// Sync
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
```

## tryLock(long timeout, TimeUnit unit)

```java
public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
	return sync.tryAcquireNanos(1, unit.toNanos(timeout));
}

public final boolean tryAcquireNanos(int arg, long nanosTimeout)
    throws InterruptedException {
    if (Thread.interrupted()) throw new InterruptedException();
    return tryAcquire(arg) || doAcquireNanos(arg, nanosTimeout);
}
```

## lockInterruptibly()

```java
public void lockInterruptibly() throws InterruptedException {
    sync.acquireInterruptibly(1);
}

// AQS
public final void acquireInterruptibly(int arg) throws InterruptedException {
    if (Thread.interrupted()) throw new InterruptedException();
    if (!tryAcquire(arg)) doAcquireInterruptibly(arg);
}
```

# Condition

## 概述

```java
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
    }
}
```

## 结构

```java
private Condition condition = lock.newCondition();

// ReentrantLock
public Condition newCondition() {
    return sync.newCondition();
}

// Sync
final ConditionObject newCondition() {
    return new ConditionObject();
}

// ConditionObject是AQS的内部类
public class ConditionObject implements Condition {
    private transient Node firstWaiter;
    private transient Node lastWaiter;
}
```

## await()

```java
public final void await() throws InterruptedException {
    if (Thread.interrupted())
        throw new InterruptedException();
    Node node = addConditionWaiter();
    int savedState = fullyRelease(node);
    int interruptMode = 0;
    while (!isOnSyncQueue(node)) {
        LockSupport.park(this);
        if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
            break;
    }
    if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
        interruptMode = REINTERRUPT;
    if (node.nextWaiter != null) // clean up if cancelled
        unlinkCancelledWaiters();
    if (interruptMode != 0)
        reportInterruptAfterWait(interruptMode);
}
```

## signal()

```java
public final void signal() {
    if (!isHeldExclusively())
        throw new IllegalMonitorStateException();
    Node first = firstWaiter;
    if (first != null)
        doSignal(first);
}
```

# Semaphore

## 结构

```java
public class Semaphore {
	private final Sync sync;
}

abstract static class Sync extends AbstractQueuedSynchronizer {
}

static final class NonfairSync extends Sync {
}

static final class FairSync extends Sync {
}

public Semaphore(int permits) {
	sync = new NonfairSync(permits);
}

public Semaphore(int permits, boolean fair) {
	sync = fair ? new FairSync(permits) : new NonfairSync(permits);
}
    
```

## acquire()

![2021632](https://tongji2021.oss-cn-shanghai.aliyuncs.com/img/2021632.jpg)

### AQS::acquireSharedInterruptibly(1)

### FairSync::tryAcquireShared(1)

<img src="https://tongji2021.oss-cn-shanghai.aliyuncs.com/img/image-20210613083050443.png" alt="image-20210613083050443" style="zoom: 80%;" />

- 通过`hasQueuedPredecessors()`查询是否有任何线程等待获取的时间比当前线程长，有则返回-1，尝试获取锁失败（**实现公平**）。
- 否则则查看资源是否还足够`remaining < 0`，不够则返回负数，尝试获取锁失败
- 如果足够，则通过CAS操作获取资源，如果CAS成功，则返回正数（remaining），获取锁成功
- 如果CAS失败，则再循环重新尝试获取锁

```java
protected int tryAcquireShared(int acquires) {
    for (;;) {
        if (hasQueuedPredecessors()) return -1;
        int available = getState();
        int remaining = available - acquires;
        if (remaining < 0 || compareAndSetState(available, remaining))
            return remaining;
    }
}
```

### 源码

```java
public void acquire() throws InterruptedException {
    sync.acquireSharedInterruptibly(1);
}

// AQS
public final void acquireSharedInterruptibly(int arg) throws InterruptedException {
    if (Thread.interrupted()) throw new InterruptedException();
    if (tryAcquireShared(arg) < 0) doAcquireSharedInterruptibly(arg);
}

// AQS
protected int tryAcquireShared(int arg) {
	throw new UnsupportedOperationException();
}

// FairSync
protected int tryAcquireShared(int acquires) {
    for (;;) {
        if (hasQueuedPredecessors()) return -1;
        int available = getState();
        int remaining = available - acquires;
        if (remaining < 0 || compareAndSetState(available, remaining))
            return remaining;
    }
}

// NonfairSync
protected int tryAcquireShared(int acquires) {
	return nonfairTryAcquireShared(acquires);
}

// Sync
final int nonfairTryAcquireShared(int acquires) {
    for (;;) {
        int available = getState();
        int remaining = available - acquires;
        if (remaining < 0 || compareAndSetState(available, remaining))
            return remaining;
    }
}
```

## release()

<img src="https://tongji2021.oss-cn-shanghai.aliyuncs.com/img/20216321.jpg" alt="20216321" style="zoom:50%;" />

### AQS::releaseShared(1)

```java
public final boolean releaseShared(int arg) {
    if (tryReleaseShared(arg)) {
        doReleaseShared();
        return true;
    }
    return false;
}
```

### Sync::tryReleaseShared(1)

使用CAS的方式尝试释放锁，因为可能有多个线程共享资源，直接调用setState()可能会冲突

```java
protected final boolean tryReleaseShared(int releases) {
    for (;;) {
        int current = getState();
        int next = current + releases;
        if (next < current)  throw new Error("Maximum permit count exceeded");
        if (compareAndSetState(current, next)) return true;
    }
}
```

### 源码

```java
public void release() {
    sync.releaseShared(1);
}

// AQS
public final boolean releaseShared(int arg) {
    if (tryReleaseShared(arg)) {
        doReleaseShared();
        return true;
    }
    return false;
}

// AQS
protected boolean tryReleaseShared(int arg) {
	throw new UnsupportedOperationException();
}

// Sync
protected final boolean tryReleaseShared(int releases) {
    for (;;) {
        int current = getState();
        int next = current + releases;
        if (next < current)  throw new Error("Maximum permit count exceeded");
        if (compareAndSetState(current, next)) return true;
    }
}
```

# AbstractQueuedSynchronizer

## 概述

简单使用案例（实现一个共享锁）：

```java
public class MyLock implements Lock {
    private final Sync sync = new Sync(2);

    private static final class Sync extends AbstractQueuedSynchronizer {
        Sync(int count) {
            if (count <= 0) throw new IllegalArgumentException();
            setState(count);
        }

        @Override
        protected int tryAcquireShared(int acquireCount) {
            while (true) {
                int cur = getState();
                int newCount = cur - acquireCount;
                if (newCount < 0 || compareAndSetState(cur, newCount)) {
                    return newCount;
                }
            }
        }

        @Override
        protected boolean tryReleaseShared(int releaseCount) {
            while (true) {
                int cur = getState();
                int newCount = cur + releaseCount;
                if (compareAndSetState(cur, newCount)) {
                    return true;
                }
            }
        }
    }

    @Override
    public void lock() {
        sync.acquireShared(1);
    }

    @Override
    public void unlock() {
        sync.releaseShared(1);
    }
}
```

## 重要属性

### state

- state用volatile修饰，保证了它的可见性。
- 如果是多线程并发修改的话，采用**compareAndSetState**来操作state
- 如果是在没有线程安全的环境下对state操作（例如ReentrantLock释放锁，因为它之前已经获取到独占锁，所以没必要用CAS），采用**setState**方法

```java
private volatile int state;

protected final int getState() {
	return state;
}

protected final void setState(int newState) {
	state = newState;
}

protected final boolean compareAndSetState(int expect, int update) {
	return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
}
```

## acquire(int arg)

### 流程

![20216141](https://tongji2021.oss-cn-shanghai.aliyuncs.com/img/20216141.jpg)

+ 首先调用子类的`tryAcquire`尝试获取一次资源
+ 获取不到则调用`addWaiter(Node.EXCLUSIVE)`将该线程加入等待队列的尾部，并标记为独占模式
+ `acquireQueued`使线程在等待队列中获取资源，中途可能不断经历阻塞/唤醒状态，一直获取到资源后才返回。如果在整个等待过程中被中断过，则返回true，否则返回false。
+ 如果线程在等待过程中被中断过，它是不响应的。只是获取资源acquireQueued返回后才再进行自我中断`selfInterrupt`，将中断补上。

```java
public final void acquire(int arg) {
    if (!tryAcquire(arg) && acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
        selfInterrupt();
}
protected boolean tryAcquire(int arg) {
    throw new UnsupportedOperationException();
}

static void selfInterrupt() {
    Thread.currentThread().interrupt();
}
```

### addWaiter(Node)

![202161413](https://tongji2021.oss-cn-shanghai.aliyuncs.com/img/202161413.jpg)

+ 将当前线程封装成一个节点（`Node.EXCLUSIVE`互斥模式、`Node.SHARED`共享模式）
+ 尝试快速入队：通过一次CAS加入到等待队列的队尾。
+ 如果**CAS失败或者队列为空**，则通过enq(node)方法初始化一个等待队列
+ 在enq(node)中，如果队列为空，则会给头部设置一个空节点：`compareAndSetHead(new Node())`，随后不断自旋直到把node加入到等待队列队尾
+ 返回当前线程所在的结点

**注意点**

如果是多线程执行，可能导致多个node.prev链接到了tail，但是通过CAS保证tail.next只会链接到其中一个Node，并且其他的Node在不断的自旋中最终还是会加入到等待队列中

**prev的有效性**：有可能产生这样一种中间状态，即node.prev指向了原先的tail，但是tail.next还没来得及指向node。这时如果另一个线程通过next指针遍历队列，就会漏掉最后一个node。但是如果是通过tail.prev来遍历等待队列，就不会漏掉节点

```java
private Node addWaiter(Node mode) {
    Node node = new Node(Thread.currentThread(), mode);
    Node pred = tail;
    if (pred != null) {
        node.prev = pred;
        if (compareAndSetTail(pred, node)) {
            pred.next = node;
            return node;
        }
    }
    enq(node);
    return node;
}

private Node enq(final Node node) {
    for (;;) {
        Node t = tail;
        if (t == null) { 
            if (compareAndSetHead(new Node()))
                tail = head;
        } else {
            node.prev = t;
            if (compareAndSetTail(t, node)) {
                t.next = node;
                return t;
            }
        }
    }
}

private final boolean compareAndSetHead(Node update) {
    return unsafe.compareAndSwapObject(this, headOffset, null, update);
}

private final boolean compareAndSetTail(Node expect, Node update) {
    return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
}
```

### acquireQueued

![202161412](https://tongji2021.oss-cn-shanghai.aliyuncs.com/img/202161412.jpg)

+ 每次循环都会判断是否可以尝试获取锁（判断前驱节点p是否为head），如果可以，那么尝试tryAcquire(arg)
+ 如果不可以尝试，或者获取锁失败，则通过parkAndCheckInterrupt阻塞线程
+ 如果线程被unpark/interrupt，则会从park中返回，接着从parkAndCheckInterrupt()返回，继续往下执行

```java
final boolean acquireQueued(final Node node, int arg) {
    boolean failed = true; 
    try {
        boolean interrupted = false;
        for (;;) {
            final Node p = node.predecessor();
            if (p == head && tryAcquire(arg)) {
                setHead(node);
                p.next = null; 
                failed = false;
                return interrupted;
            }
            if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt())
                interrupted = true;
        }
    } finally {
        if (failed) cancelAcquire(node); // 该方法不会被执行
    }
}

private void setHead(Node node) {
    head = node;
    node.thread = null;
    node.prev = null;
}

private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
    int ws = pred.waitStatus;
    if (ws == Node.SIGNAL) return true;
    if (ws > 0) {
        do {
            node.prev = pred = pred.prev;
        } while (pred.waitStatus > 0);
        pred.next = node;
    } else {
        compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
    }
    return false;
}

private final boolean parkAndCheckInterrupt() {
    LockSupport.park(this);
    return Thread.interrupted();
}
```

### 注意点

+ 整个过程忽略用户发出的中断信号（也就是由于线程获取同步状态失败后进入同步队列中，后续对线程进行中断操作时，线程不会从同步队列中移出），直到acquireQueued执行结束后，才通过selfInterrupt恢复用户的中断

## release(int arg)

```java
public final boolean release(int arg) {
    if (tryRelease(arg)) {
        Node h = head;
        if (h != null && h.waitStatus != 0)
            unparkSuccessor(h);
        return true;
    }
    return false;
}

protected boolean tryRelease(int arg) {
    throw new UnsupportedOperationException();
}

private void unparkSuccessor(Node node) {
    int ws = node.waitStatus;
    if (ws < 0) compareAndSetWaitStatus(node, ws, 0);
    Node s = node.next;
    if (s == null || s.waitStatus > 0) {
        s = null;
        for (Node t = tail; t != null && t != node; t = t.prev)
            if (t.waitStatus <= 0) s = t;
    }
    if (s != null) LockSupport.unpark(s.thread);
}
```

## tryAcquireNanos(int arg, long nanosTimeout)

```java
public final boolean tryAcquireNanos(int arg, long nanosTimeout)
    throws InterruptedException {
    if (Thread.interrupted()) throw new InterruptedException();
    return tryAcquire(arg) || doAcquireNanos(arg, nanosTimeout);
}

private boolean doAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
    if (nanosTimeout <= 0L) return false;
    final long deadline = System.nanoTime() + nanosTimeout;
    final Node node = addWaiter(Node.EXCLUSIVE);
    boolean failed = true;
    try {
        for (;;) {
            final Node p = node.predecessor();
            if (p == head && tryAcquire(arg)) {
                setHead(node);
                p.next = null; // help GC
                failed = false;
                return true;
            }
            nanosTimeout = deadline - System.nanoTime();
            if (nanosTimeout <= 0L) return false;
            if (shouldParkAfterFailedAcquire(p, node) &&
                nanosTimeout > spinForTimeoutThreshold)
                LockSupport.parkNanos(this, nanosTimeout);
            if (Thread.interrupted()) throw new InterruptedException();
        }
    } finally {
        if (failed) cancelAcquire(node);
    }
}
```

## acquireInterruptibly(int arg)

```java
public final void acquireInterruptibly(int arg) throws InterruptedException {
    if (Thread.interrupted()) throw new InterruptedException();
    if (!tryAcquire(arg)) doAcquireInterruptibly(arg);
}

private void doAcquireInterruptibly(int arg) throws InterruptedException {
    final Node node = addWaiter(Node.EXCLUSIVE);
    boolean failed = true;
    try {
        for (;;) {
            final Node p = node.predecessor();
            if (p == head && tryAcquire(arg)) {
                setHead(node);
                p.next = null; // help GC
                failed = false;
                return;
            }
            if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt())
                throw new InterruptedException();
        }
    } finally {
        if (failed) cancelAcquire(node);
    }
}
```

## acquireSharedInterruptibly(int arg)

```java
public final void acquireSharedInterruptibly(int arg) throws InterruptedException {
    if (Thread.interrupted()) throw new InterruptedException();
    if (tryAcquireShared(arg) < 0) doAcquireSharedInterruptibly(arg);
}

protected int tryAcquireShared(int arg) {
    throw new UnsupportedOperationException();
}

private void doAcquireSharedInterruptibly(int arg) throws InterruptedException {
    final Node node = addWaiter(Node.SHARED);
    boolean failed = true;
    try {
        for (;;) {
            final Node p = node.predecessor();
            if (p == head) {
                int r = tryAcquireShared(arg);
                if (r >= 0) {
                    setHeadAndPropagate(node, r);
                    p.next = null; // help GC
                    failed = false;
                    return;
                }
            }
            if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt())
                throw new InterruptedException();
        }
    } finally {
        if (failed) cancelAcquire(node);
    }
}
```

## releaseShared(int arg)

```java
public final boolean releaseShared(int arg) {
    if (tryReleaseShared(arg)) {
        doReleaseShared();
        return true;
    }
    return false;
}

protected boolean tryReleaseShared(int arg) {
	throw new UnsupportedOperationException();
}

private void doReleaseShared() {
    for (;;) {
        Node h = head;
        if (h != null && h != tail) {
            int ws = h.waitStatus;
            if (ws == Node.SIGNAL) {
                if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0)) continue;           
                unparkSuccessor(h);
            }
            else if (ws == 0 && !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                continue;               
        }
        if (h == head) break;
    }
}
```