# 概览

## 继承体系

![image-20210601175023607](https://tongji2021.oss-cn-shanghai.aliyuncs.com/img/image-20210601175023607.png)

+ HashMap：不保证遍历顺序，非线程安全
+ LinkedHashMap：HashMap的子类，保持了记录插入顺序，遍历会得到先插入的Node，也可以按照访问顺序排列，实现LRU算法
+ TreeMap：默认按照key升序排列
+ Hashtable：线程安全，效率低，所有方法加synchronized修饰，需要线程安全场景用ConcurrentHashMap即可

## 数据结构

数组+链表+红黑树

![image-20210601212902002](https://tongji2021.oss-cn-shanghai.aliyuncs.com/img/image-20210601212902002.png)

```java
static class Node<K,V> implements Map.Entry<K,V> {
    final int hash;
    final K key;
    V value;
    Node<K,V> next;
}

Node<K,V>[] table; // 第一次put时初始化，length总是2的幂次倍
int size; // HashMap中实际存在的键值对数量
int modCount; // 记录HashMap内部结构发生变化的次数，主要用于迭代的快速失败。
int threshold; // 所能容纳的最大Node(键值对)个数。threshold = length * Load factor,超过这个数目就重新resize(扩容)
final float loadFactor; 
transient Set<Map.Entry<K， V>> entrySet;

static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; // 默认初始容量
static final int MAXIMUM_CAPACITY = 1 << 30; // 最大数组容量
static final float DEFAULT_LOAD_FACTOR = 0.75f; // 默认负载因子为0.75
static final int TREEIFY_THRESHOLD = 8;  // 单个Node下的值的个数大于8时候，会将链表转换成为红黑树。
static final int UNTREEIFY_THRESHOLD = 6; // 单个Node节点下的红黑树节点的个数小于6时候，会将红黑树转化成为链表。
static final int MIN_TREEIFY_CAPACITY = 64; // 树化的最小数组容量
```

## 特性

+ 运行key为null（hash为null），存放在entry数组的第0个位置上

+ 如果put操作中，只改变了value，则modCount不变

+ 要求映射中的key是不可变对象。不可变对象是该对象在创建后它的哈希值不会被改变。如果对象的哈希值发生变化，Map对象很可能就定位不到映射的位置了（因为put和get都需要靠hashCode找到他的索引下标）。

+ 当同一个索引位置的节点在增加后达到8个时，并且此时数组的长度大于等于 64，则则会触发链表节点（Node）转红黑树节点（TreeNode），转成红黑树节点后，其实链表的结构还存在，通过 next 属性维持。

    当单个桶中元素数量小于6时，进行反树化

+ HashMap查找添加元素的时间复杂度都为O(1)。数组的查询效率为O(1)，链表的查询效率是O(k)，红黑树的查询效率是O(log k)，k为桶中的元素个数

+ 为它的子类LinkedHashMap提供一些钩子方法

# 基本流程

## put()

1. 计算key对应的hashCode
2. 在entry数组较小时也能让高位也参与hash的运算：`hash = (h = key.hashCode()) ^ (h >>> 16)`
3. 如果table不存在，则先初始化
4. 如果索引`i = (n - 1) & hash`的位置为null，则直接新建节点
5. 看table[i]首节点是否和key相同，相同则覆盖value，返回oldValue，判断是否相同的方法：hash相同，并且equals方法也相同
6. 如果是红黑树节点，调用红黑树节点的插入方法
7. 遍历下拉链表，如果找到了value就覆盖，找到链表尾部就新建Node，并且查看是否需要进行树化
8. 插入成功后查看是否需要扩容、modCount++、提供钩子方法等

```java
public V put(K key, V value) {
    return putVal(hash(key), key, value, false, true);
}

static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}

// 核心部分
final V putVal(int hash, K key, V value) {
    if ((tab = table) == null || (n = tab.length) == 0)
        n = (tab = resize()).length;
    if ((p = tab[i = (n - 1) & hash]) == null)
        tab[i] = newNode(hash, key, value, null);
    else {
        Node<K,V> e; K k;
        if (p.hash == hash && ((k = p.key) == key || (key != null && key.equals(k))))
            e = p;
        else if (p instanceof TreeNode)
            e = ((TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value);
        else {
            for (int binCount = 0; ; ++binCount) {
                if ((e = p.next) == null) {
                    p.next = newNode(hash, key, value, null); // 尾插法
                    if (binCount >= TREEIFY_THRESHOLD - 1) // -1 for 1st
                        treeifyBin(tab, hash);
                    break;
                }
                if (e.hash == hash &&
                    ((k = e.key) == key || (key != null && key.equals(k))))
                    break;
                p = e;
            }
        }
        if (e != null) { // existing mapping for key
            V oldValue = e.value;
            e.value = value;
            afterNodeAccess(e);
            return oldValue;
        }
    }
    ++modCount;
    if (++size > threshold)
        resize();
    afterNodeInsertion(evict);
    return null;
}
```

### 注意事项

+ 当length总是2的n次方时，h& (length-1)运算等价于对length取模，也就是h%length，但是&比%具有更高的效率。

## get()

+ 获取对应hash
+ 看table是否存在，以及`tab[(n - 1) & hash]`是否存在
+ 检查索引下的第一个节点的hash、equals是否相同
+ 如果是红黑树节点，调用红黑树节点方法搜索
+ 在下拉链表中搜索

```java
public V get(Object key) {
    Node<K,V> e;
    return (e = getNode(hash(key), key)) == null ? null : e.value;
}
final Node<K,V> getNode(int hash, Object key) {
    Node<K,V>[] tab; Node<K,V> first, e; int n; K k;
    if ((tab = table) != null && (n = tab.length) > 0 &&
            (first = tab[(n - 1) & hash]) != null) {
        if (first.hash == hash && 
                ((k = first.key) == key || (key != null && key.equals(k))))
            return first;
        if ((e = first.next) != null) {
            if (first instanceof TreeNode)
                return ((TreeNode<K,V>)first).getTreeNode(hash, key);
            do {
                if (e.hash == hash &&
                        ((k = e.key) == key || (key != null && key.equals(k))))
                    return e;
            } while ((e = e.next) != null);
        }
    }
    return null;
}
```

### 注意事项

+ 两种情况会返回null：value不存在、put的value是null

## 扩容机制

如果没有初始化，则首先执行初始化，否则执行正常的扩容操作。将容量变成原来的两倍

遍历原Entry数组，把所有的Entry重新Hash到新数组

```java
final Node<K, V>[] resize() {
    Node<K, V>[] newTab = (Node<K, V>[]) new Node[newCap];
    for (int j = 0; j < oldCap; ++j) {
        Node<K, V> e;
        if ((e = oldTab[j]) != null)
        {
            oldTab[j] = null; // 将老表的节点设置为空, 以便垃圾收集器回收空间
            if (e.next == null) // 只有一个节点,直接散列
                newTab[e.hash & (newCap - 1)] = e;
            else if (e instanceof TreeNode) // 调用红黑树的重新散列
                ((TreeNode<K, V>) e).split(this, newTab, j, oldCap);
            else {
                Node<K, V> loHead = null, loTail = null;
                Node<K, V> hiHead = null, hiTail = null;
                Node<K, V> next;
                do {
                    next = e.next;
                    if ((e.hash & oldCap) == 0) { // 分成两条链表，使用尾插法
                        if (loTail == null)
                            loHead = e;
                        else
                            loTail.next = e;
                        loTail = e;
                    } else {
                        if (hiTail == null)
                            hiHead = e;
                        else
                            hiTail.next = e;
                        hiTail = e;
                    }
                } while ((e = next) != null);
                if (loTail != null) {
                    loTail.next = null;
                    newTab[j] = loHead;
                }
                if (hiTail != null) {
                    hiTail.next = null;
                    newTab[j + oldCap] = hiHead;
                }
            }
        }
    }
    return newTab;
}
```

例如00011,10011，如果oldCap=10000(16)
00011 & 10000 = 0
10011 & 10000 = 10000 != 0
将原本在一个索引的节点分成两条链表，一个放在原位置处，一个放在[原位置 + oldCap]处

# 常用方法

## containsKey ()

```java
public boolean containsKey(Object key) {
	return getNode(hash(key), key) != null;
}
```

## getOrDefault()

```
public V getOrDefault(Object key, V defaultValue) {
    Node<K,V> e;
    return (e = getNode(hash(key), key)) == null ? defaultValue : e.value;
}
```

## tableSizeFor（）

得到第一个大于等于cap的二的幂次方

用于从构造函数中获取用户输入，并且保证了 table 数组的长度总是 2 的次幂

无符号右移：就是右移之后，无论该数为正还是为负，右移之后左边都是补上0

```java
public HashMap(int initialCapacity, float loadFactor) {
    this.loadFactor = loadFactor;
    this.threshold = tableSizeFor(initialCapacity);
}
static final int tableSizeFor(int cap) {
    int n = cap - 1;
    n |= n >>> 1;
    n |= n >>> 2;
    n |= n >>> 4;
    n |= n >>> 8;
    n |= n >>> 16;
    return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
}
```

例如10001(17)
首先cap-1:     10000
n |= n >>> 1: n=(10000 | 01000) =11000
n |= n >>> 2: n=(11000 | 00110) =11110
n |= n >>> 4: n=(11110 | 00001) =11111
n |= n >>> 8: n=(11111 | 00000) =11111
n |= n >>> 16: n=(11111 | 00000) =11111
return n+1:100000(32)
...
因为int为32位,所以为了保证结果,最后右移16位结束
使得要求的数字1开始的后面全为1

## 迭代器HashIterator

+ 初始化记录modCount当前值，并让next指向第一个非空元素，而current=null
+ nextNode方法，首先查看modCount值以决定是否fail-fast
+ 然后检查当前元素是否为空，为空则抛出NoSuchElementException异常
+ 最后让current等于当前元素，next继续指向下一个非空元素
+ hasNext方法则简单判断next是否为空即可
+ remove方法首先判断当前节点是否为空，为空则抛出IllegalStateException异常
+ 随后查看modCount值决定是否fail-fast
+ 删除该节点，且修改expectedModCount，即迭代器中的remove方法不会导致fail-fast

```java
abstract class HashIterator
{
    Node<K， V> next;        
    Node<K， V> current;     
    int expectedModCount;  // for fast-fail
    int index;             

    HashIterator()
    {
        expectedModCount = modCount;
        Node<K， V>[] t = table;
        current = next = null;
        index = 0;
        if (t != null && size > 0)
        { 
            do
            {
            } while (index < t.length && (next = t[index++]) == null);
            // 让next能指向第一个非空的元素
        }
    }

    public final boolean hasNext()
    {
        return next != null;
    }

    final Node<K， V> nextNode()
    {
        Node<K， V>[] t;
        Node<K， V> e = next;
        if (modCount != expectedModCount) // fail-fast机制
            throw new ConcurrentModificationException();
        if (e == null)
            throw new NoSuchElementException();
        //找到下一个非空元素
        if ((next = (current = e).next) == null && (t = table) != null)
        {
            do
            {
            } while (index < t.length && (next = t[index++]) == null);
        }
        return e;
    }

    public final void remove()
    {
        Node<K， V> p = current;
        if (p == null)
            throw new IllegalStateException();
        if (modCount != expectedModCount) // 依然是fail-fast
            throw new ConcurrentModificationException();
        current = null;
        K key = p.key;
        removeNode(hash(key)， key， null， false， false);
        expectedModCount = modCount; // 此处remove不会触发fail-fast
    }
}
```

# 扩展

## JDK1.7和JDK1.8中HashMap有什么区别？

+ 数据结构上：JDK1.8中当链表长度大于阈值（默认为8）时，将链表转化为红黑树，以减少搜索时间。
+ 插入数据方式上：
    + JDK1.7使用头插法插入元素，在多线程的环境下有可能导致环形链表的出现，扩容的时候会导致死循环（导致死循环的主要原因是扩容后，节点的顺序会反掉）。
    + JDK1.8使用尾插法插入元素（直接插入到链表尾部/红黑树），解决了多线程死循环问题，但仍是非线程安全的，多线程时可能会造成数据丢失问题。

## HashMap线程安全问题

例如两个线程同时调用put操作，他们同时进入到了if语句后，先写入的Node会被后写入的Node覆盖掉

```java
if ((p = tab[i = (n - 1) & hash]) == null)
            tab[i] = newNode(hash, key, value, null);
```

## 为什么不直接用红黑树？

红黑树节点的大小是普通节点的两倍、红黑树需要进行左旋，右旋，变色这些操作来保持平衡，新增节点的效率低，而单链表不需要。当元素小于 8 个的时候，此时做查询操作，链表结构已经能保证查询性能。当元素大于 8 个的时候， 红黑树搜索时间复杂度是 O(logn)，而链表是 O(n)，此时需要红黑树来加快查询速度。

## 为什么链表改为红黑树的阈值是 8?

理想情况下使用随机的哈希码，容器中节点分布在 hash 桶中的频率遵循**泊松分布**，按照泊松分布的计算公式计算出桶中元素个数和概率的对照表，链表中元素个数为 8 时的概率非常小。

## 默认加载因子为什么是 0.75？

作为一般规则，默认负载因子（0.75）在时间和空间成本上提供了很好的折衷，是对空间和时间效率的一个平衡选择。较高的值会降低空间开销，但提高查找成本。较低的值浪费空间。

## JDK1.7死循环

+ 扩容操作中，把旧表中所有Node重新计算索引下标并散列到新表对应索引处(采用头插法)
+ 把新表的引用赋值给table



首先会导致链表反转：

<img src="https://tongji2021.oss-cn-shanghai.aliyuncs.com/img/image-20210601204630630.png" alt="image-20210601204630630" style="zoom: 67%;" />

如果线程1、线程2同时操作，线程2执行到e = A、next = B时切换到线程1，线程1执行完resize导致链表反转成C->B->A->null。

线程2继续执行以下代码，则产生了死循环：

```
e.next = newTable[i];
newTable[i] = e;
e = next;
```

<img src="https://tongji2021.oss-cn-shanghai.aliyuncs.com/img/image-20210601205841811.png" alt="image-20210601205841811" style="zoom:67%;" />

```java
void resize(int newCapacity)
{
    Entry[] oldTable = table;
    Entry[] newTable = new Entry[newCapacity];
    transfer(newTable);
    table = newTable;
}

void transfer(Entry[] newTable)
{
    for (Entry<K, V> e : table)  // 遍历旧表中每个Entry
    {
        while (null != e) // 对不为空的(链表)进行操作
        {
            Entry<K, V> next = e.next;
            e.hash = null == e.key ? 0 : hash(e.key);
            int i = indexFor(e.hash, newCapacity); // 通过新的容量大小散列得到新的索引
            e.next = newTable[i];
            newTable[i] = e;
            e = next;
        }
    }
}
```

## hashCode与equals

### equals

在未重写equals方法我们是继承了Object的equals方法，默认比较两个对象的内存地址

+ 对于值对象，==比较的是两个对象的值
+ 对于引用对象，==比较的是两个对象的地址

```java
// 默认情况是:
public boolean equals(Object obj) {
    return (this == obj);
}
// 一般会重写equals方法以比较两个对象的内容
public boolean equals(Object obj){
    if(this==obj) return true;
    if(obj==null) return false;
    if(getClass()!=obj.getClass()) return false;

    Person person = (Person) obj;
    return Objects.equals(name， person.name) &&Objects.equals(age， person.age);
}
所以基本数据类型用==判断相等，引用数据类型都用equals()进行判断(String也是引用类型)
即==是判断两个变量或实例是不是指向同一个内存空间 equals是判断两个变量或实例所指向的内存空间的值是不是相同
==指引用是否相同 equals()指的是值是否相同
```

### hashcode

```java
因为HashSet或HashMap等集合中判断元素是否相等用到了hashcode是否相等，所以为避免我们认为相等但是逻辑判断却不相等的情况出现，自定义类重写equals必须重写hashcode方法
public native int hashCode();

无论何时覆盖该方法，通常需要覆盖`hashCode`方法，以便维护`hashCode`方法的通用合同，该方法规定相等的对象必须具有相等的哈希码。

@Override
public int hashCode()
{
    return Objects.hash(username， age);
}
```

+ 如果两个对象相等，则hashcode一定也是相同的

+ 两个对象相等，对两个equals方法返回true

+ 两个对象有相同的hashcode值，它们也不一定是相等的

+ hashCode()的默认行为是对堆上的对象产生独特值。如果没有重写hashCode()，则该class的两个对象无论如何都不会相等（即使这两个对象指向相同的数据）。

### 例子

使用Set存放学生信息，此时不小心对同一个人录入了两次（学号和姓名相同，但不是同一个对象）（我们逻辑上认为是同一个人，并且已重写了equals()方法，但是没有重写hashcode()方法）。这时Set中按逻辑应该只有一个学生，但是实际上却有两个

这是因为我们没有重写父类（Object）的hashcode方法，Object的hashcode方法会根据两个对象的地址生成对相应的hashcode；s1和s2是分别new出来的，那么他们的地址肯定是不一样的，自然hashcode值也会不一样。

Set区别对象的标准是，两个对象hashcode是不是一样，再判定两个对象是否equals；此时因为hashCode不一样，所以Set判定他们不是同一个对象，在Set中该学生信息就出现了两次