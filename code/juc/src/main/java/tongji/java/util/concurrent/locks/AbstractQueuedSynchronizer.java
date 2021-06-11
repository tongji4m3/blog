package tongji.java.util.concurrent.locks;

import sun.misc.Unsafe;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * rely on first-in-first-out (FIFO) wait queues
 * rely on a single atomic int value to represent state.
 * <p>
 * Subclasses must define the protected methods that change this state, and which define what that state means in terms of this object being acquired or released.
 * <p>
 * 共享锁与独占锁的区别在于:
 * 独占锁是线程独占的，同一时刻只有一个线程能拥有独占锁，AQS里将这个线程放置到exclusiveOwnerThread成员上去。
 * 共享锁是线程共享的，同一时刻能有多个线程拥有共享锁，但AQS里并没有用来存储获得共享锁的多个线程的成员。
 * 如果一个线程刚获取了共享锁，那么在其之后等待的线程也很有可能能够获取到锁。但独占锁不会这样做，因为锁是独占的。
 * 当然，如果一个线程刚释放了锁，不管是独占锁还是共享锁，都需要唤醒在后面等待的线程。
 */
public class AbstractQueuedSynchronizer {
    // 初始化state为0
    protected AbstractQueuedSynchronizer() {
    }

    /**
     * Insertion into a CLH queue requires only a single atomic operation on "tail", so there is a simple atomic point of demarcation from unqueued to queued.
     * Similarly, dequeuing involves only updating the "head". However, it takes a bit more work for nodes to determine who their successors are, in part to deal with possible cancellation due to timeouts and interrupts.
     * <p>
     * The "prev" links , are mainly needed to handle cancellation. If a node is cancelled, its successor is (normally) relinked to a non-cancelled predecessor.
     * We also use "next" links to implement blocking mechanics. The thread id for each node is kept in its own node, so a predecessor signals the next node to wake up by traversing next link to determine which thread it is.
     * <p>
     * Threads waiting on Conditions use the same nodes, but use an additional link. Conditions only need to link nodes in simple (non-concurrent) linked queues because they are only accessed when exclusively held.
     * Upon await, a node is inserted into a condition queue.
     * Upon signal, the node is transferred to the main queue.
     * A special value of status field is used to mark which queue a node is on.
     */
    static final class Node {
        static final Node SHARED = new Node(); // Marker to indicate a node is waiting in shared mode
        static final Node EXCLUSIVE = null; // Marker to indicate a node is waiting in exclusive mode


        static final int CANCELLED = 1;
        static final int SIGNAL = -1;
        static final int CONDITION = -2;
        static final int PROPAGATE = -3; // indicate the next acquireShared should unconditionally propagate

        volatile int waitStatus;
        volatile Node prev;
        volatile Node next;
        volatile Thread thread;

        /**
         * Link to next node waiting on condition, or the special value SHARED. Because condition queues are accessed only when holding in exclusive mode, we just need a simple linked queue to hold nodes while they are waiting on conditions. They are then transferred to the queue to re-acquire. And because conditions can only be exclusive, we save a field by using special value to indicate shared mode.
         * 表明当前node的线程是想要获取共享锁还是独占锁。注意，这个成员只是这个作用，不是用来连接双向链表的。
         */
        Node nextWaiter;

        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        final Node predecessor() throws NullPointerException {
            Node p = prev;
            if (p == null) throw new NullPointerException();
            return p;
        }

        Node() {
        }

        Node(Thread thread, Node mode) {     // Used by addWaiter
            this.nextWaiter = mode;
            this.thread = thread;
        }

        Node(Thread thread, int waitStatus) { // Used by Condition
            this.waitStatus = waitStatus;
            this.thread = thread;
        }
    }

    //
    /**
     * 头结点，固定是一个dummy node，因为它的thread成员固定为null
     * 即使等待线程只有一个，等待队列中的节点个数也肯定是2个，因为第一个节点总是dummy node。
     * 采用lazily initialized
     */
    private volatile Node head;
    private volatile Node tail;
    private volatile int state;

    static final long spinForTimeoutThreshold = 1000L;

    // This operation has memory semantics of a volatile read.
    protected final int getState() {
        return state;
    }

    protected final void setState(int newState) {
        state = newState;
    }

    protected final boolean compareAndSetState(int expect, int update) {
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }


    /**
     * 对于独占锁，AQS的state代表代表锁的状态，为0代表没有线程持有锁，非0代表有线程持有了锁。
     * 获得了锁的线程会将自己设置为exclusiveOwnerThread。
     * addWaiter负责new出一个包装当前线程的node，enq负责将node添加到队尾，如果队尾为空，它还负责添加dummy node。
     * acquireQueued是整个获取锁过程的核心，这里是指它的那个死循环。一般情况下，每次循环做的事就是：尝试获取锁，获取锁失败，阻塞，被唤醒。如果某一次循环获取锁成功，那么之后会返回到用户代码调用处。
     * shouldParkAfterFailedAcquire负责自身的前驱节点的状态设置为SIGNAL，这样，当前驱节点释放锁时，会根据SIGNAL来唤醒自身。
     * parkAndCheckInterrupt最简单，用来阻塞当前线程。它也会去检查中断状态。
     * <p>
     * Acquires in exclusive mode, ignoring interrupts. Implemented by invoking at least once tryAcquire, returning on success.
     * Otherwise the thread is queued, possibly repeatedly blocking and unblocking, invoking tryAcquire until success.
     * <p>
     * 再等到当前线程获取锁成功后，那么acquireQueued返回的就一定是true了。再回到acquire的逻辑，发现需要进入if分支，再执行selfInterrupt()将中断状态补上，
     * 这样下一次Thread.interrupted()就能返回true了。为的就是在 回到用户代码之前，把中断状态补上，万一用户需要中断状态呢。
     * <p>
     * tryAcquire返回false时，必将调用addWaiter和acquireQueued。
     * addWaiter是AQS的实现，因为开始获取锁失败了（tryAcquire返回false），所以需要把当前线程包装成node放到等待队列中，返回代表当前线程的node。
     */
    public final void acquire(int arg) {
        if (!tryAcquire(arg) && acquireQueued(addWaiter(Node.EXCLUSIVE), arg)) {
            selfInterrupt();
        }
    }

    /**
     * 复原中断状态，虽然这个版本的函数不用响应中断。当acquireQueued返回真时，代表这期间函数曾经检测到过中断状态，并且将中断状态消耗掉了
     * 所以需要在退出acquire之前，将中断状态重新设置上。
     */
    static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }

    /**
     * Attempts to acquire in exclusive mode. This method should query if the state of the object permits it to be acquired in the exclusive mode, and if so to acquire it.
     * <p>
     * 虽然AQS是一个抽象类，但却没有任何抽象方法。如果定义为抽象方法确实不合适，因为继承使用AQS并不一定需要使用到AQS提供的所有功能（独占锁和共享锁），这样子类反而需要实现所有抽象方法。
     * 如果定义为空实现的普通方法，虽然不需要子类实现所有空方法了，但这样还是不够明确。现在AQS将这些方法的实现为抛出UnsupportedOperationException异常，那么如果是子类需要使用的方法，就覆盖掉它；如果是子类不需要使用的方法，一旦调用就会抛出异常。
     * <p>
     * tryAcquire用来获取独占锁一次，try的意思就是只试一次，要么成功，要么失败。
     */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 既然执行到了addWaiter，说明当前线程第一次执行tryAcquire时失败了。
     * 既然获取锁失败了，那么就需要将当前线程包装一个node，放到等待队列的队尾上去，以后锁被释放时别人就会通过这个node来唤醒自己。
     * <p>
     * return:不管是提前return，还是执行完enq再return，当return时，已经是将代表当前线程的node放到队尾了。注意，返回的是，代表当前线程的node。
     */
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
        // 执行到这里，有两种情况：
        // 1.队列为空。head和tail成员从来没有初始化过
        // 2.CAS操作失败。当执行compareAndSetTail时，tail成员已经被修改了
        enq(node);
        return node;
    }

    /**
     * 利用了自旋（循环）和CAS操作，保证了node放到队尾。
     * <p>
     * 发现要完成双向链表的指针指向，需要经过3步：
     * 将参数node的前驱指向tail
     * CAS设置参数node为tail
     * 如果CAS成功，则修正tail的后继
     * <p>
     * enq的尾分叉：从上面的步骤可以看出，如果存在很多个线程都刚好执行到了node.prev = t这里，那么CAS失败的线程不能成功入队，此时它们的prev还暂时指向的旧tail。
     * <p>
     * prev的有效性：从上图第二步可以看到，此时线程1的node已经是成功放到队尾了，但此时队列却处于一个中间状态，前一个node的next还没有指向队尾呢。
     * 此时，如果另一个线程如果通过next指针遍历队列，就会漏掉最后那个node；但如果另一个线程通过tail成员的prev指针遍历队列，就不会漏掉node了。
     * prev的有效性也解释了AQS源码里遍历队列时，为什么常常使用tail成员和prev指针来遍历，比如你看unparkSuccessor。
     */
    private Node enq(final Node node) {
        for (; ; ) {
            Node t = tail;
            if (t == null) {
                /*
                就算只有一个线程入队，入队完毕后队列将有两个node，第一个node称为dummy node，因为它的thread成员为null;
                第二个node才算是实际意义的队头，它的thread成员不为null。

                新建的是空node，它的所有成员都是默认值。thread成员为null，waitStatus为0。之后你会发现，队尾node的waitStatus总是0，因为默认初始化。
                 */
                if (compareAndSetHead(new Node())) {
                    tail = head;
                }
            } else {
                node.prev = t;
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return t;
                }
            }
        }
    }

    /**
     * 它解释了独占锁获取的整个过程。执行到这个函数，说明：
     * 当前线程已经执行完了addWaiter方法。
     * 传入的node的thread成员就是当前线程。
     * 传入的node已经成功入队。（addWaiter的作用）
     * <p>
     * 每次循环都会判断是否可以尝试获取锁（p == head），如果可以，那么尝试（tryAcquire(arg)）。
     * 如果尝试获取锁成功，那么函数的使命就达到了，执行完相应收尾工作，然后返回。
     * 如果 不可以尝试 或者 尝试获取锁却失败了，那么阻塞当前线程（parkAndCheckInterrupt）。
     * 如果当前线程被唤醒了，又会重新走这个流程。被唤醒时，是从parkAndCheckInterrupt处唤醒，然后从这里继续往下执行。
     * <p>
     * 执行acquireQueued的线程一定是node参数的thread成员，虽然执行过程中，可能会经历不断 阻塞和被唤醒 的过程。
     */
    //
    final boolean acquireQueued(final Node node, int arg) {
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (; ; ) {
                // 因为从enq的逻辑可知，head只会是一个dummy node，实际意义的node只会在head的后面。
                // 而node的前驱是head（final Node p = node.predecessor()），则代表node已经是队列中的第一个实际node了，排在最前面的node自然可以尝试去获取锁了。
                final Node p = node.predecessor();
                /*
                为什么刚执行完addWaiter方法时，才把代表当前线程的node放到队尾，怎么之后一判断就会发现自己处于head的后继了？
                这个问题不考虑上面的特殊场景，而考虑addWaiter时，队列中有许多node。
                其实这很合理，这说明从head到当前方法栈中的node之间的那些node，它们自己也会在执行acquireQueued，它们依次执行成功（指p == head && tryAcquire(arg)成功），每次执行成功相当于把head成员从队列上后移一个node，当它们都执行完毕，当前方法栈中的node自然也就是head的后继了。
                注意，“之间的那些node”的最后一个node执行acquireQueued成功后（代表最后一个node的代表线程获得锁成功，它自己成为了head），当前方法还在阻塞之中，只有当这“最后一个node”释放独占锁时，才会执行unparkSuccessor(head)，当前线程才会被唤醒。

                 */
                /*
                回想整个调用过程，是最开始在acquire里调用tryAcquire就已经失败了，然而此时第一次循环时，又可能马上去调tryAcquire（说可能，是因为需要p == head成立），这会不会是一次肯定失败的tryAcquire？
                考虑这种场景，线程1获取了锁正在使用还没释放，此时队列为空，线程2此时也来获取锁，自然最开始在acquire里调用tryAcquire会失败，假设线程2刚开始执行acquireQueued，此时线程1释放了锁，此时线程2肯定排在head后面，那么线程2马上tryAcquire，然后就可以获取成功。
                 */
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null;
                    failed = false;
                    return interrupted;
                }
                // 执行shouldParkAfterFailedAcquire前，线程在此次循环中，已经尝试过获取锁了，但还是失败了。就会阻塞当前线程
                // 如果当前线程被唤醒了，又会重新走这个流程。被唤醒时，是从parkAndCheckInterrupt处唤醒，然后从这里继续往下执行。
                if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
                    interrupted = true;
                }
            }
        } finally {
            /*
            虽然号称此函数是不响应中断的函数，但不响应中断只是对于AQS的使用者来说，如果一个线程阻塞在parkAndCheckInterrupt这里，别的线程来中断它，它是会马上唤醒的，然后继续这个循环。
            不过想要退出这个函数，只有通过return interrupted，而前一句就是failed = false，所以finally块里，是永远不会去执行cancelAcquire(node)的。
             */
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    /**
     * 从逻辑中可见，如果当前线程获取锁成功，代表当前线程的node会新设置自己为head，然后将其弄成dummy node，
     * 即把node的thread成员清空，但这个被清空的thread成员已经在tryAcquire里将这个thread设置为了exclusiveOwnerThread。
     * <p>
     * 使用setHead而非compareAndSetHead，因为此时不需要CAS操作，执行到这里说明当前线程已经获得了独占锁（tryAcquire成功），所以别的线程是不可能同时执行这部分代码的。
     */
    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }

    /**
     * 简单来说，就是：跳过无效前驱，把node的有效前驱（有效是指node不是CANCELLED的）找到，并且将有效前驱的状态设置为SIGNAL，之后便返回true代表马上可以阻塞了。
     * <p>
     * shouldPark：该函数的返回值影响是否可以执行parkAndCheckInterrupt函数。
     * AfterFailedAcquire：指的是 获取锁失败了才会执行该函数。其实具体指两种情况：
     * 1. p == head为false，即当前线程的node的前驱不是head
     * 2. 虽然 p == head为true，但parkAndCheckInterrupt返回false了，即当前线程虽然已经排到等待队列的最前面，但获取锁还是失败了。
     * <p>
     * node一共有四种状态，但在独占锁的获取和释放过程中，我们只可能将node的状态变成CANCELLED或SIGNAL，
     * 而shouldParkAfterFailedAcquire函数就会把一个node的状态变成SIGNAL。注意，一个node新建的时候，它的waitStatus是默认初始化为0的。
     * <p>
     * CANCELLED，一个node的状态是CANCELLED，说明这个node的代表线程已经取消等待了。
     * SIGNAL，一个node的状态是SIGNAL，说明这个node的后继node的代表线程已经阻塞或马上阻塞（shouldParkAfterFailedAcquire设置前驱为SIGNAL后，
     * 下一次的acquireQueued循环可能就会阻塞了，所以说“已经阻塞或马上阻塞”），并且当前node成为head并释放锁时，会根据SIGNAL来唤醒后继node。即SIGNAL是唤醒后继节点的标志。
     * 有趣的是，CANCELLED状态与当前node关联，SIGNAL状态与后继node关联。
     * <p>
     * 如果前驱节点的状态为SIGNAL，说明闹钟标志已设好，返回true表示设置完毕。
     * 如果前驱节点的状态为CANCELLED，说明前驱节点本身不再等待了，需要跨越这些节点，然后找到一个有效节点，再把node和这个有效节点的前驱后继连接好。
     * 如果是其他情况，那么CAS尝试设置前驱节点为SIGNAL。
     * <p>
     * <p>
     * 由于shouldParkAfterFailedAcquire函数在acquireQueued的调用中处于一个死循环中，且因为shouldParkAfterFailedAcquire函数若返回false，且考虑当前线程一直不能获取到锁的情况，那么此函数必将至少执行两次才能阻塞自己。
     * shouldParkAfterFailedAcquire只有在检测到前驱的状态为SIGNAL才能返回true，只有true才会执行到parkAndCheckInterrupt。
     * shouldParkAfterFailedAcquire返回false后，进入下一次循环，当前线程又会再次尝试获取锁（p == head && tryAcquire(arg)）。或者说，每次执行shouldParkAfterFailedAcquire，都说明当前循环 尝试过获取锁了，但失败了。
     * 如果刚开始前驱的状态为0，那么需要第一次执行compareAndSetWaitStatus(pred, ws, Node.SIGNAL)返回false并进入下一次循环，第二次才能进入if (ws == Node.SIGNAL)分支，所以说 至少执行两次。
     * 死循环保证了最终一定能设置前驱为SIGNAL成功的。（考虑当前线程一直不能获取到锁）
     */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        int ws = pred.waitStatus;
        if (ws == Node.SIGNAL)
            /*
             * 前驱节点已经设置了SIGNAL，闹钟已经设好，现在我可以安心睡觉（阻塞）了。
             * 如果前驱变成了head，并且head的代表线程exclusiveOwnerThread释放了锁，
             * 就会来根据这个SIGNAL来唤醒自己
             */
            return true;
        if (ws > 0) {
            /*
             * 发现传入的前驱的状态大于0，即CANCELLED。说明前驱节点已经因为超时或响应了中断，
             * 而取消了自己。所以需要跨越掉这些CANCELLED节点，直到找到一个<=0的节点
             */
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;
        } else {
            /*
             * 进入这个分支，ws只能是0或PROPAGATE。
             * CAS设置ws为SIGNAL
             */
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        return false;
    }

    /**
     * parkAndCheckInterrupt函数执行后，当前线程就会被挂起了，也就是我们所说的阻塞。
     * <p>
     * 调用完LockSupport.park(this)，当前线程就阻塞在这里，直到有别的线程unpark了当前线程，或者中断了当前线程。
     * 而返回的Thread.interrupted()代表当前线程在阻塞的过程中，有没有被别的线程中断过，如果有，则返回true。注意，Thread.interrupted()会消耗掉中断的状态，即第一次执行能返回true，但紧接着第二次执行就只会返回false了。
     * <p>
     * 如果是别的线程unpark了当前线程，那么调用Thread.interrupted()返回false。
     * 如果是别的线程中断了当前线程，那么调用Thread.interrupted()返回true。
     */
    private final boolean parkAndCheckInterrupt() {
        LockSupport.park(this);
        // Tests whether the current thread has been interrupted. The interrupted status of the thread is cleared by this method.
        return Thread.interrupted();
    }


    // Cancels an ongoing attempt to acquire.

    /**
     * 清理状态
     * node不再关联到任何线程
     * node的waitStatus置为CANCELLED
     * node出队:包括三个场景下的出队：
     * node是tail
     * node既不是tail，也不是head的后继节点
     * node是head的后继节点
     * <p>
     * cancelAcquire()是一个出队操作，出队要调整队列的head、tail、next和prev指针。
     * 对于next指针和tail，cancelAcquire()使用了一堆CAS方法，本着一种别人不上，我上，别人上过了，我不能再乱上了的态度。这是一种积极主动的做事方式。
     * 而对于prev指针和head，cancelAcquire()则是完全交给别的线程来做，感觉像是lazy模式。
     * 为何是这样的实现呢？为何不全采用lazy模式，或者是全采用积极主动的方式？
     * 这似乎和prev指针是可靠的，而next指针是不可靠的有关，也或许有性能方面的考虑
     */
    private void cancelAcquire(Node node) {
        if (node == null) return;

        node.thread = null;

        // 循环用来跳过无效前驱
        Node pred = node.prev;
        while (pred.waitStatus > 0)
            node.prev = pred = pred.prev;

        // pred的后继无论如何都需要取消，因为即使前面循环没有执行，
        // 之后有些CAS操作会尝试修改pred的后继，如果CAS失败，那么说明有别的线程在做取消动作或通知动作，所以当前线程也不需要更多的动作了。
        Node predNext = pred.next;

        // 这里直接使用赋值操作，而不是CAS操作。
        // 如果别的线程在执行这步之后，别的线程将会跳过这个node。
        // 如果别的线程在执行这步之前，别的线程还是会将这个node当作有效节点。
        node.waitStatus = Node.CANCELLED;

        // 如果node是tail，更新tail为pred，并使pred.next指向null
        if (node == tail && compareAndSetTail(node, pred)) {
            compareAndSetNext(pred, predNext, null);
        } else {
            int ws;
            // node既不是tail，也不是head的后继节点
            if (pred != head && ((ws = pred.waitStatus) == Node.SIGNAL || (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) && pred.thread != null) {
                Node next = node.next;
                /*
                调用了compareAndSetNext()方法将pred.next指向successor.是实际上起到出队作用的
                不过，还少了一步呀。将successor指向pred是谁干的？是别的线程做的。当别的线程在调用cancelAcquire()或者shouldParkAfterFailedAcquire()时，会根据prev指针跳过被cancel掉的前继节点，同时，会调整其遍历过的prev指针。
                 */
                if (next != null && next.waitStatus <= 0) compareAndSetNext(pred, predNext, next);
            } else {
                //
                /*
                如果node是head的后继节点，则直接唤醒node的后继节点
                出队操作实际上是由unparkSuccessor()唤醒的线程执行的。unparkSuccessor()会唤醒successor关联的线程（暂称为sthread），当sthread被调度并恢复执行后，将会实际执行出队操作。
                sthread当初就是被parkAndCheckInterrupt()给挂起的，恢复执行时，也从此处开始重新执行。sthread将会重新执行for循环，此时，node尚未出队，successor的前继节点依然是node，而不是head。所以，sthread会执行到shouldParkAfterFailedAcquire()处。而从场景2中可以得知，shouldParkAfterFailedAcquire()中将会调整successor的prev指针（同时也调整head的next指针），从而完成了node的出队操作。

                被cancel的node是head的后继节点，是队列中唯一一个有资格去尝试获取资源的节点。他将资格放弃了，自然有义务去唤醒他的后继来接棒。
                 */
                unparkSuccessor(node);
            }
            node.next = node; // help GC
        }
    }

    private void unparkSuccessor(Node node) {
        // 一般为SIGNAL，然后将其设置为0.但允许这次CAS操作失败
        int ws = node.waitStatus;
        // 首先会尝试设置状态从小于0变成0。一般可以这样认为，如果head的状态为0，代表head后继线程即将被唤醒，或者已经被唤醒。
        if (ws < 0) compareAndSetWaitStatus(node, ws, 0);

        /*
         * head后继一般能直接通过next找到，但只有prev是肯定有效的。
         * 所以遇到next为null，肯定需要从队尾的prev往前找。
         * 遇到next的状态为取消，也需要从队尾的prev往前找。
         */
        Node s = node.next;
        // 如果遇到s == null，说明我们遇到一种中间状态，next指针还没有指好。如果遇到s.waitStatus > 0，说明head后继刚取消了。这两种情况，都需要从队尾的prev往前找。
        if (s == null || s.waitStatus > 0) {
            s = null; // 上面两种情况，都需要先置s为null，因为真正后继需要通过循环才能找到
            // 从后往前循环会一直给s赋值，所以s找到的会是第一个应该唤醒的线程
            // 循环肯定能找到node之后第一个不是取消状态的节点。
            for (Node t = tail; t != null && t != node; t = t.prev)
                if (t.waitStatus <= 0) s = t;
        }
        if (s != null)
            LockSupport.unpark(s.thread);
        /*
        head后继当初是阻塞在了parkAndCheckInterrupt()这里，根据它的返回值，我们将设置interrupted变量，以在返回用户代码之前，补上中断状态。
被唤醒的线程将开始下一次循环，最重要的，会再一次执行p == head && tryAcquire(arg)，考虑本文的流程且没有别的线程竞争的话，此处的tryAcquire必定能成功。
         */
    }

    /**
     * 释放锁的过程，根本不会区分 公平或不公平、响应中断或不响应中断、超时或不超时。
     * 这是因为，这些区别都只是存在于尝试获取锁的方式上而已，既然已经获得了锁，也就不需要有这些区别。
     */
    public final boolean release(int arg) {
        if (tryRelease(arg)) {
            Node h = head;
            /*
            h != null标准地防止空指针，不过这种情况肯定也有，比如从头到尾都只有一个线程在使用锁，那么队列也不会初始化，head肯定为null。

            使用h.waitStatus != 0作为唤醒head后继的判断标准，当队列只有一个dummy node时，它的状态为0，也就不会执行unparkSuccessor(h)了。
            当head的状态为SIGNAL时，说明head后继已经设置了闹钟，会执行unparkSuccessor(h)。
             */
            if (h != null && h.waitStatus != 0) {
                unparkSuccessor(h);
                return true;
            }
        }
        return false;
    }

    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }


    /**
     * 进入这个方法后，会第一次进行tryAcquire尝试。但不同的，此acquireInterruptibly函数中，会去检测Thread.interrupted()，并抛出异常。
     * <p>
     * 对于acquireInterruptibly这个方法而言，既可以是公平的，也可以是不公平的，这完全取决于tryAcquire的实现（即取决于ReentrantLock当初是怎么构造的）。
     */
    public final void acquireInterruptibly(int arg) throws InterruptedException {
        if (Thread.interrupted()) throw new InterruptedException();
        if (!tryAcquire(arg)) doAcquireInterruptibly(arg);
    }

    /**
     * doAcquireInterruptibly不需要返回值，因为该函数中如果检测到了中断状态，就直接抛出异常就好了。
     * <p>
     * doAcquireInterruptibly方法的finally块是可能会执行到cancelAcquire(node)的，而acquireQueued方法不可能去执行cancelAcquire(node)的。
     * 在doAcquireInterruptibly方法中，如果线程阻塞在parkAndCheckInterrupt这里后，别的线程来中断阻塞线程，阻塞线程会被唤醒，然后抛出异常。
     * 本来抛出异常该函数就马上结束掉的，但由于有finally块，所以在结束掉之前会去执行finally块，并且由于failed为true，则会执行cancelAcquire(node)。
     */
    private void doAcquireInterruptibly(int arg) throws InterruptedException {
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (; ; ) {
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


    /**
     * 对于tryAcquireNanos这个方法而言，既可以是公平的，也可以是不公平的，这完全取决于tryAcquire的实现（即取决于ReentrantLock当初是怎么构造的）。
     */
    public final boolean tryAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (Thread.interrupted()) throw new InterruptedException();
        return tryAcquire(arg) || doAcquireNanos(arg, nanosTimeout);
    }

    /**
     * 每次循环都会检查时间是否到达deadline。
     * 当剩余时间小于spinForTimeoutThreshold时，则不能调用LockSupport.parkNanos，因为时间太短，反而无法精确控制阻塞时间，所以不如在剩余的时间里一直循环。
     * LockSupport.parkNanos除了会因为别人的park而唤醒，也会因为别人的中断而唤醒，当然最重要的，时间到了，它自己会唤醒。
     * 不管哪种情况，被唤醒后，都会检查中断状态。每个循环都会检查一次。
     */
    private boolean doAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (nanosTimeout <= 0L) return false;
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return true;
                }
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L) return false;
                if (shouldParkAfterFailedAcquire(p, node) && nanosTimeout > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if (Thread.interrupted()) throw new InterruptedException();
            }
        } finally {
            if (failed) cancelAcquire(node);
        }
    }


    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0)
            doAcquireShared(arg);
    }

    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 两点不同
     * 创建的节点不同。共享锁使用addWaiter(Node.SHARED)，所以会创建出想要获取共享锁的节点。而独占锁使用addWaiter(Node.EXCLUSIVE)。
     * 获取锁成功后的善后操作不同。共享锁使用setHeadAndPropagate(node, r)，因为刚获取共享锁成功后，后面的线程也有可能成功获取，所以需要在一定条件唤醒head后继。而独占锁使用setHead(node)。
     */
    private void doAcquireShared(int arg) {
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        if (interrupted)
                            selfInterrupt();
                        failed = false;
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * 如果一个线程刚获取了共享锁，那么在其之后等待的线程也很有可能能够获取到锁
     */
    private void setHeadAndPropagate(Node node, int propagate) {
        Node h = head;
        setHead(node);
        if (propagate > 0 || h == null || h.waitStatus < 0 || (h = head) == null || h.waitStatus < 0) {
            Node s = node.next;
            if (s == null || s.isShared()) doReleaseShared();
        }
    }

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

    /**
     * 共享锁的逻辑则直接调用了doReleaseShared，但在获取共享锁成功时，也可能会调用到doReleaseShared。
     * 也就是说，获取共享锁的线程（分为：已经获取到的线程 即执行setHeadAndPropagate中、等待获取中的线程 即阻塞在shouldParkAfterFailedAcquire里）
     * 和释放共享锁的线程 可能在同时执行这个doReleaseShared。
     *
     * 共享锁与独占锁的最大不同，是共享锁可以同时被多个线程持有，虽然AQS里面没有成员用来保存持有共享锁的线程们。
     * 由于共享锁在获取锁和释放锁时，都需要唤醒head后继，所以将其逻辑抽取成一个doReleaseShared的逻辑了。
     */
    private void doReleaseShared() {
        for (; ; ) {
            Node h = head;
            // 判断队列是否至少有两个node，如果队列从来没有初始化过（head为null），或者head就是tail，那么中间逻辑直接不走，直接判断head是否变化了。
            if (h != null && h != tail) {
                int ws = h.waitStatus;
                if (ws == Node.SIGNAL) {
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0)) continue;
                    unparkSuccessor(h); // 如果状态为SIGNAL，说明h的后继是需要被通知的。通过对CAS操作结果取反，将compareAndSetWaitStatus(h, Node.SIGNAL, 0)和unparkSuccessor(h)绑定在了一起。说明了只要head成功得从SIGNAL修改为0，那么head的后继的代表线程肯定会被唤醒了。
                } else if (ws == 0 && !compareAndSetWaitStatus(h, 0, Node.PROPAGATE)) {
                    continue;
                }
            }
            /*
            循环检测到head没有变化时就会退出循环
            head变化一定是因为：acquire thread被唤醒，之后它成功获取锁，然后setHead设置了新head。
            而且注意，只有通过if(h == head) break;即head不变才能退出循环，不然会执行多次循环。
            保证了，只要在某个循环的过程中有线程刚获取了锁且设置了新head，就会再次循环。目的当然是为了再次执行unparkSuccessor(h)，即唤醒队列中第一个等待的线程。
             */
            if (h == head) break;
        }
    }

    /**
     * 和Object的wait()\notify()相同之处，理解Condition接口的实现：
     * 调用wait()的线程必须已经处于同步代码块中，换言之，调用wait()的线程已经获得了监视器锁；调用await()的线程则必须是已经获得了lock锁。
     * 执行wait()时，当前线程会释放已获得的监视器锁，进入到该监视器的等待队列中；执行await()时，当前线程会释放已获得的lock锁，然后进入到该Condition的条件队列中。
     * 退出wait()时，当前线程又重新获得了监视器锁；退出await()时，当前线程又重新获得了lock锁。
     * 调用监视器的notify，会唤醒等待在该监视器上的线程，这个线程此后才重新开始锁竞争，竞争成功后，会从wait方法处恢复执行；调用Condition的signal，会唤醒等待在该Condition上的线程，这个线程此后才重新开始锁竞争，竞争成功后，会从await方法处恢复执行。
     *
     * 对于每个Condition对象来说，都对应到一个条件队列condition queue。而对于每个Lock对象来说，都对应到一个同步队列sync queue。
     * 每一个Condition对象都对应到一个条件队列condition queue，而每个线程在执行await()后，都会被包装成一个node放到condition queue中去。
     * condition queue是一个单向链表，它使用nextWaiter作为链接。这个队列中，不存在dummy node，每个节点都代表一个线程。这个队列的节点的状态，我们只关心状态是否为CONDITION，如果是CONDITION的，说明线程还等待在这个Condition对象上；如果不是CONDITION的，说明这个节点已经前往sync queue了。
     *
     * 假设现在存在一个Lock对象和通过这个Lock对象生成的若干个Condition对象，从队列上来说，就存在了一个sync queue和若干个与这个sync queue关联的condition queue。本来这两种队列上的节点没有关系，但现在有了signal方法，就会使得condition queue上的节点会跑到sync queue上去。
     * 节点从从condition queue转移到sync queue上去的过程。即使是调用signalAll时，节点也是一个一个转移过去的，因为每个节点都需要重新建立sync queue的链接。
     *
     * 如果一个节点刚入队sync queue，说明这个节点的代表线程没有获得锁（尝试获得锁失败了）。
     * 如果一个节点刚出队sync queue（指该节点的代表线程不在同步队列中的任何节点上，因为它已经跑到了AQS的exclusiveOwnerThread成员上去了），说明这个节点的代表线程刚获得了锁（尝试获得锁成功了）。
     * 如果一个节点刚入队condition queue，说明这个节点的代表线程此时是有锁了，但即将释放。
     * 如果一个节点刚出队condition queue，因为前往的是sync queue，说明这个节点的代表线程此时是没有获得锁的。
     *
     * 对于ReentrantLock来说，我们使用newCondition方法来获得Condition接口的实现，而ConditionObject就是一个实现了Condition接口的类。
     *
     * ConditionObject又是AQS的一个成员内部类，这意味着不管生成了多少个ConditionObject，它们都持有同一个AQS对象的引用，这和“一个Lock可以对应到多个Condition”相吻合。这也意味着：对于同一个AQS来说，只存在一个同步队列sync queue，但可以存在多个条件队列condition queue。
     *
     * 成员内部类有一个好处，不管哪个ConditionObject对象都可以调到同一个外部类AQS对象的方法上去。比如acquireQueued方法，这样，不管node在哪个condition queue上，最终它们离开后将要前往的地方总是同一个sync queue。
     */
    public class ConditionObject implements Condition {
        /**
         * firstWaiter和lastWaiter都不再需要加volatile来保证可见性了。这是因为源码作者是考虑，使用者肯定是以获得锁的前提下来调用await() / signal()这些方法的，既然有了这个前提，那么对firstWaiter的读写肯定是无竞争的，既然没有竞争也就不需要 CAS+volatile 来实现一个乐观锁了。
         */
        private Node firstWaiter;
        private Node lastWaiter;

        public ConditionObject() {
        }

        /**
         * 明确会有哪些线程在执行：
         * 执行await的当前线程。这个线程是最开始调用await的线程，也是执行await所有调用链的线程，它被包装进局部变量node中。（后面会以node线程来称呼它）
         * 执行signal的线程。这个线程会改变await当前线程的node的状态state，使得await当前线程的node前往同步队列，并在一定条件在唤醒await当前线程。
         * 中断await当前线程的线程。你就当这个线程只是用来唤醒await当前线程，并改变其中断状态。只不过await当前线程它自己被唤醒后，也会做和上一条同样的事情：“使得await当前线程的node前往同步队列”。
         * 执行unlock的线程。如果await当前线程的node已经是同步队列的head后继，那么获得独占锁的线程在释放锁时，就会唤醒 await当前线程。
         *
         * 从用户角度来说，执行await \ signal \ unlock的前提都是线程必须已经获得了锁。
         * todo 可恶的Condition！！！
         */
        @Override
        public final void await() throws InterruptedException {
//            if (Thread.interrupted()) throw new InterruptedException(); // 在调用await之前，当前线程就已经被中断了，那么抛出异常
//            Node node = addConditionWaiter(); // 将当前线程包装进Node,然后放入当前Condition的条件队列
//            int savedState = fullyRelease(node); // 释放锁，不管当前线程重入锁多少次，都要释放干净
//            int interruptMode = 0;
//            // 如果当前线程node不在同步队列上，说明还没有别的线程调用 当前Condition的signal。
//            // 第一次进入该循环，肯定会符合循环条件，然后park阻塞在这里
//            while (!isOnSyncQueue(node)) {
//                LockSupport.park(this);
//                /* 如果被唤醒，要么是因为别的线程调用了signal使得当前node进入同步队列，
//                 进而当前node等到自己成为head后继后并被唤醒。
//                 要么是因为别的线程 中断了当前线程。
//                 如果接下来发现自己被中断过，需要检查此时signal有没有执行过，
//                 且不管怎样，都会直接退出循环。*/
//                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
//                    break;
//            }
//            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
//                interruptMode = REINTERRUPT;
//            if (node.nextWaiter != null) // clean up if cancelled
//                unlinkCancelledWaiters();
//            if (interruptMode != 0)
//                reportInterruptAfterWait(interruptMode);
        }

        @Override
        public void awaitUninterruptibly() {

        }

        @Override
        public long awaitNanos(long nanosTimeout) throws InterruptedException {
            return 0;
        }

        @Override
        public boolean await(long time, TimeUnit unit) throws InterruptedException {
            return false;
        }

        @Override
        public boolean awaitUntil(Date deadline) throws InterruptedException {
            return false;
        }

        @Override
        public void signal() {

        }

        @Override
        public void signalAll() {

        }
    }


    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;
    private static final long waitStatusOffset;
    private static final long nextOffset;

    static {
        try {
            stateOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.Node.class.getDeclaredField("waitStatus"));
            nextOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.Node.class.getDeclaredField("next"));

        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    private final boolean compareAndSetHead(AbstractQueuedSynchronizer.Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    private final boolean compareAndSetTail(AbstractQueuedSynchronizer.Node expect, AbstractQueuedSynchronizer.Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    private static final boolean compareAndSetWaitStatus(AbstractQueuedSynchronizer.Node node,
                                                         int expect,
                                                         int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset,
                expect, update);
    }

    private static final boolean compareAndSetNext(AbstractQueuedSynchronizer.Node node,
                                                   AbstractQueuedSynchronizer.Node expect,
                                                   AbstractQueuedSynchronizer.Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }
}
