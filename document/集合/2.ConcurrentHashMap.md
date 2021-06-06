> 源码经过本人理解，只截取关键部分，如有错误，请指正！

# 数据结构

```java
static class Node<K,V> implements Map.Entry<K,V> {
    final int hash;
    final K key;
    volatile V val;
    volatile Node<K,V> next;
}

// 在扩容时发挥作用，hash值为MOVED(-1)，存储nextTable的引用
final class ForwardingNode<K,V> extends Node<K,V> {
    final Node<K,V>[] nextTable;
    ForwardingNode(Node<K,V>[] tab) {
        super(MOVED, null, null, null);
        this.nextTable = tab;
    }
}

volatile Node<K,V>[] table;
volatile Node<K,V>[] nextTable; // 默认为null，扩容时新生成的数组，其大小为原数组的两倍。
volatile int sizeCtl; // 控制初始化和扩容的


static final int MOVED     = -1;  // 表示正在扩容
```

+ Node节点设置了volatile关键字修饰，致使它每次获取的都是**最新**设置的值
+ 抛弃了Segment分段锁机制，利用CAS+Synchronized来保证并发更新的安全，底层采用数组+链表+红黑树的存储结构。
+ 在构造函数中只会初始化sizeCtl值，并不会直接初始化table，而是延缓到第一次put操作。
+ ConcurrentHashMap不允许key或value为null值



- 在集合**新建而未初始化**时，sizeCtl用于记录初始容量大小
- 在集合**初始化过程中**，sizeCtl值设置为 -1 表示集合正在初始化中，其他线程发现该值为 -1 时会让出CPU资源以便初始化操作尽快完成 。
- 集合**初始化完成后**，sizeCtl 用于记录当前集合的负载容量值，也就是触发集合扩容的极限值 。
- 集合**正在扩容时**，sizeCtl 用于记录当前扩容的并发线程数情况，该状态下 sizeCtl < 0 。

# 操作流程

## put

ConcurrentHashMap添加数据时，采取了CAS+synchronize结合策略。首先会判断该节点是否为null，如果为null，尝试使用CAS添加节点；如果添加失败，说明发生了并发冲突，再对节点进行上锁并插入数据。在并发较低的情景下无需加锁，可以显著提高性能。同时只会CAS尝试一次，也不会造成线程长时间等待浪费cpu时间的情况。

```java
final V putVal(K key, V value) {
    if (key == null || value == null) throw new NullPointerException();
    int hash = spread(key.hashCode());
    int binCount = 0;

    for (Node<K,V>[] tab = table;;) {
        Node<K,V> f; int n, i, fh;
        if (tab == null || (n = tab.length) == 0)
            tab = initTable();
        else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
            if (casTabAt(tab, i, null, new Node<K,V>(hash, key, value, null)))
                break;                   
        }
        else if ((fh = f.hash) == MOVED) 
            tab = helpTransfer(tab, f);
        else {
            V oldVal = null;
            synchronized (f) { // f是要插入的索引下标上的首节点
                if (tabAt(tab, i) == f) {
                    if (fh >= 0) {
                        // 尾查法往链表添加节点
                    }
                    else if (f instanceof TreeBin) {
                        // 调用红黑树添加节点方法
                    }
                }
            }
            if (binCount != 0) {
                if (binCount >= TREEIFY_THRESHOLD)
                    treeifyBin(tab, i);
                if (oldVal != null)
                    return oldVal;
                break;
            }
        }
    }
    addCount(1L, binCount);
    return null;
}

static final int spread(int h) {
    return (h ^ (h >>> 16)) & HASH_BITS; // 0x7fffff
}
```

+ 判断键值是否为`null`，为`null`抛出异常。
+ 调用`spread()`方法计算key的hashCode()获得哈希地址
+ 如果当前table为空，则初始化table，需要注意的是这里并没有加`synchronized`，也就是允许多个线程去**尝试**初始化table，但是在初始化函数里面使用了`CAS`保证只有一个线程去执行初始化过程。
+ 使用`i = (n - 1) & hash`计算出待插入键值的下标，如果该下标上的bucket为`null`，则直接调用实现`CAS`原子性操作的`casTabAt()`方法将节点插入到table中，如果插入成功则完成put操作，结束返回。插入失败(被别的线程抢先插入了)则继续往下执行。
+ 如果该下标上的节点(头节点)的哈希地址为MOVED(-1)，说明当前f是ForwardingNode节点，意味有其它线程正在扩容，该线程执行`helpTransfer()`方法协助扩容。
+ 如果该下标上的bucket不为空，且又不需要扩容，则进入到bucket中，同时**使用synchroized锁住这个bucket**，注意只是锁住该下标上的bucket而已，其他的bucket并未加锁，其他线程仍然可以操作其他未上锁的bucket
+ 进入到bucket里面，首先判断这个bucket存储的是红黑树还是链表。
+ 如果是**链表**，则遍历链表看看是否有hash和equals相同的节点，有的话则根据传入的参数进行覆盖或者不覆盖，没有找到相同的节点的话则将新增的节点**插入到链表尾部**。如果是**红黑树**，则将节点插入。到这里**解锁**。
+ 最后判断该bucket上的链表长度是否大于**链表转红黑树的阈值(8)**，大于则将链表转成红黑树。
+ 调用`addCount()`方法，将键值对数量+1，并检查是否需要扩容。

## get

```java
public V get(Object key) {
    int h = spread(key.hashCode());
    if ((tab = table) != null && (n = tab.length) > 0 &&
            (e = tabAt(tab, (n - 1) & h)) != null) {
        if ((eh = e.hash) == h) { // 头节点寻找
            if ((ek = e.key) == key || (ek != null && key.equals(ek)))
                return e.val;
        }
        else if (eh < 0) 
            // 红黑树中寻找
        while ((e = e.next) != null) { 
            // 链表中寻找
        }
    }
    return null;
}
```

1. 调用`spread()`方法计算key的hashCode()获得哈希地址。
2. 如果table不为空，对应key所在bucket不为空：`tabAt(tab, (n - 1) & h))`，则到bucket中查找。
3. 如果头节点hash、equals相同，则返回头节点值
4. 如果bucket的头节点的hash小于0，则代表这个bucket存储的是红黑树，则在红黑树中查找。
5. 如果bucket头节点的哈希地址不小于0，则代表bucket为链表，遍历链表，找到则返回该键key的值，找不到则返回null。

## remove

1. 调用`spread()`方法计算出键key的哈希地址。
2. 计算出键key所在的数组下标，如果table为空或者bucket为空，则返回`null`。
3. 判断当前table是否正在扩容，如果在扩容则调用helpTransfer方法协助扩容。
4. 如果table和bucket都不为空，table也不处于在扩容状态，则**锁住当前bucket**，对bucket进行操作。
5. 根据bucket的头结点判断bucket是链表还是红黑树。
6. 在链表或者红黑树中移除哈希地址、键key相同的节点。
7. 调用`addCount`方法，将当前table存储的键值对数量-1。

## 初始化

```java
public ConcurrentHashMap(int initialCapacity) {
    int cap = tableSizeFor(initialCapacity);
    this.sizeCtl = cap;
}

// 第一次put时才调用
private final Node<K,V>[] initTable() {
    Node<K,V>[] tab; int sc;
    while ((tab = table) == null || tab.length == 0) {
        if ((sc = sizeCtl) < 0)
            Thread.yield(); 
        else if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
            if ((tab = table) == null || tab.length == 0) {
                table = (Node<K,V>[])new Node<?,?>[n];
                sizeCtl = n - (n >>> 2);
            }
            break;
        }
    }
    return tab;
}
```

+ table的初始化只能由一个线程完成，但是每个线程都可以争抢去初始化table。
+ 首先，判断table是否为null，即需不需要首次初始化，如果某个线程进到这个方法后，其他线程已经将table初始化好了，那么该线程结束该方法返回。
+ 如果table为null，进入到while循环，如果`sizeCtl`小于0(其他线程正在对table初始化)，那么该线程调用`Thread.yield()`挂起该线程，让出CPU时间，该线程也从运行态转成就绪态，等该线程从就绪态转成运行态的时候，别的线程已经table初始化好了，那么该线程结束while循环，结束初始化方法返回。如果从就绪态转成运行态后，table仍然为`null`，则继续while循环。
+ 如果table为null且`sizeCtl`不小于0，则调用实现`CAS`原子性操作的`compareAndSwap()`方法将sizeCtl设置成-1，告诉别的线程我正在初始化table，这样别的线程无法对table进行初始化。如果设置成功，则初始化table，容量大小为默认的容量大小(16)，或者为sizeCtl。其中sizeCtl的初始化是在构造函数中进行的。并设置sizeCtl的值为数组长度的3/4（`threshold`的作用），当ConcurrentHashMap储存的键值对数量大于这个阈值，就会发生扩容。

## 扩容

### 触发扩容

1. 添加新元素后，元素个数达到扩容阈值触发扩容。
2. 调用 putAll 方法，发现容量不足以容纳所有元素时候触发扩容。

3. 某个槽内的链表长度达到 8，但是数组长度小于 64 时候触发扩容。

### 扩容操作

- 构建一个nextTable，大小为table的两倍。这个过程只能只有单个线程进行nextTable的初始化（通过Unsafe.compareAndSwapInt修改sizeCtl值，保证只有一个线程能够初始化nextTable）
- 将原来table里面的内容复制到nextTable中，这个步骤是允许**多线程**操作的，所以性能得到提升，减少了扩容的时间消耗。

### 扩容时其他操作

扩容状态下其他线程对集合进行插入、修改、删除等操作时遇到 ForwardingNode 节点会调用helpTransfer方法帮助扩容

# 扩展

## JDK1.7的ConcurrentHashMap

+ 基本思想是将数据分为一段一段的存储，然后给每一段数据配一把锁，当一个线程占用锁访问其中一个段数据时，其他段的数据也能被其他线程访问。
+ Segment 是一个ReentrantLock，每一个Segment元素存储的是HashEntry数组+链表，每一个Segment其实就相当于一个HashMap
+ put操作需要加锁，get操作不用加锁（通过使用volatile和巧妙的操作保证同步）
+ ConcurrentHashMap定位一个元素的过程需要进行两次Hash操作，第一次Hash定位到Segment，第二次Hash定位到元素所在的链表的头部

![20216212](https://tongji2021.oss-cn-shanghai.aliyuncs.com/img/20216212.png)

```java
final Segment<K,V>[] segments;

static final class Segment<K,V> extends ReentrantLock {
	volatile HashEntry<K,V>[] table;
	int count;
	int modCount;
	int threshold;
	float loadFactor;
}
static final class HashEntry<K,V> { 
    final K key; 
    final int hash; 
    volatile V value; 
    final HashEntry<K,V> next; 
}
```

## ConcurrentHashMap不允许key或value为null值

put操作中有相应的判断：

```java
if (key == null || value == null) throw new NullPointerException();
```

但这样设计的原因是避免二义性：假定ConcurrentHashMap也可以存放value为null的值。那调用map.get(key)时如果返回了null，有两重含义:

**1.这个key从来没有在map中映射过。**

**2.这个key的value在设置的时候，就是null。**

对于HashMap来说，它的正确使用场景是在单线程下使用。所以在单线程中，当我们得到的value是null的时候，可以用hashMap.containsKey(key)方法来区分上面说的两重含义。

而ConcurrentHashMap的使用场景为多线程。假设concurrentHashMap允许存放值为null的value。这时有A、B两个线程。线程A调用concurrentHashMap.get(key)方法，返回为null，我们还是不知道这个null是没有映射的null还是存的值就是null。

假设此时返回null的真实情况是因为这个key没有在map里面映射过。用concurrentHashMap.containsKey(key)来验证假设是否成立，期望的结果是返回false。

但是在我们调用concurrentHashMap.get(key)方法之后，containsKey方法之前，有一个线程B执行了concurrentHashMap.put(key,null)的操作。那么我们调用containsKey方法返回的就是true了。这就与我们的假设的真实情况不符合了。	