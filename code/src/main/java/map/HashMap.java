package map;

import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.util.Objects;

public class HashMap<K, V> {

    private static class Node<K, V> {
        final int hash;
        final K key;
        V value;
        Node<K, V> next;

        public Node(int hash, K key, V value, Node<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    private static class TreeNode<K, V> extends Node<K, V> {
        TreeNode<K, V> left;
        TreeNode<K, V> right;

        public TreeNode(int hash, K key, V value, Node<K, V> next) {
            super(hash, key, value, next);
        }
    }


    private Node<K, V>[] table;
    private int capacity;
    private int size;
    private int modCount;
    private final float loadFactory;

    private static final float DEFAULT_LOAD_FACTORY = 0.75F;
    private static final int DEFAULT_CAPACITY = 1 << 4;
    private static final int TREE_THRESHOLD = 8;
    private static final int UNTREE_THRESHOLD = 6;
    private static final int MIN_TREE_CAPACITY = 64;

    public HashMap() {
        this(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTORY);
    }

    public HashMap(int capacity) {
        this(capacity, DEFAULT_LOAD_FACTORY);
    }

    public HashMap(int capacity, float loadFactory) {
        this.loadFactory = loadFactory;
        this.capacity = tabSizeFor(capacity);
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void put(K key, V value) {
        if (table == null) {
            initial();
        }
        int hash = hash(key);
        int index = (size - 1) & hash;
        if (table[index] == null) {
            table[index] = new Node<>(hash, key, value, null);
        } else if (table[index] instanceof TreeNode) {
            // 调用红黑树的插入方法
        } else {
            // 链表插入方法,如果相同则更改,并且返回旧值，否则尾部插入
            // 如果数量达到8则树化
        }
        // 提供钩子方法给LinkedHashMap
        size++;
        if (size > loadFactory * capacity) {
            resize(capacity << 1);
        }
        modCount++;
    }

    private void resize(int newCapacity) {
        Node<K, V>[] newTable = (Node<K, V>[]) new Node[newCapacity];
        for (int i = 0; i < table.length; i++) {
            if (table[i] != null) {
                Node<K, V> e = table[i];
                table[i] = null;
                if (e.next == null) {
                    newTable[e.hash & (newCapacity - 1)] = e;
                } else if (e instanceof TreeNode) {
                    // 调用红黑树的resize()方法
                } else {
                    // 尾插法将链表分为两条
                    Node<K, V> oldList = null;
                    Node<K, V> newList = null;
                    while (e != null) {
                        if ((e.hash & capacity) == 0) {
                            if (oldList == null) {
                                oldList = e;
                            } else {
                                oldList.next = e;
                            }
                        } else {
                            if (newList == null) {
                                newList = e;
                            } else {
                                newList.next = e;
                            }
                        }
                        e = e.next;
                    }
                    newTable[i] = oldList;
                    newTable[i + capacity] = newList;
                }
            }
        }
        table = newTable;
        capacity = newCapacity;
        modCount++;
    }

    public V get(K key) {
        int hash = hash(key);
        Node<K, V> first, cur;
        if (table == null || (first = table[(table.length - 1) & hash]) == null) return null;
        if (first.hash == hash && ((Objects.equals(key, first.key)))) {
            return first.value;
        }
        if ((cur = first.next) != null) {
            if (first instanceof TreeNode) {
                // 调用红黑树查找方法,并返回
            }
            // 调用链表查找方法
            do {
                if (cur.hash == hash && ((Objects.equals(key, cur.key)))) {
                    return cur.value;
                }
            } while ((cur = cur.next) != null);
        }
        return null;
    }

    public int size() {
        return size;
    }

    private int hash(K key) {
        int hash;
        return key == null ? 0 : (hash = key.hashCode()) ^ (hash >>> 16);
    }

    // 返回大于等于它的第一个二的幂次方
    private int tabSizeFor(int n) {
        n = n - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return n + 1;
    }

    public boolean containsKey(K key) {
        return get(key) != null;
    }

    private void initial() {
        table = (Node<K, V>[]) new Node[capacity];
    }

    class HashIterator {
        Node<K, V> next;
        Node<K, V> current;
        int expectedModCount;
        int index;

        public HashIterator() {
            expectedModCount = modCount;
            Node<K, V>[] t = table;
            current = next = null;
            index = 0;
            if (t != null && size > 0) {
                do {

                } while (index < t.length && (next = t[index++]) == null);
            }
        }

        public final boolean hasNext() {
            return next != null;
        }

        final Node<K, V> nextNode() {
            Node<K, V>[] t;
            Node<K, V> e = next;
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            if (e == null) {
                throw new NoSuchElementException();
            }
            if ((next = (current = e).next) == null && (t = table) != null) {
                do {
                } while (index < t.length && (next = t[index++]) == null);
            }
            return e;
        }

        public final void remove() {
            Node<K, V> p = current;
            if (p == null) {
                throw new IllegalStateException();
            }
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            current = null;
            K key = p.key;
            // 从HashMap中删除
            expectedModCount = modCount;
        }
    }

    public static void main(String[] args) {
        HashMap<String, String> hashMap = new HashMap<>();
        System.out.println(hashMap.get("a"));
        hashMap.put("a", "a");
        hashMap.put("b", "b");
        hashMap.put("c", "c");
        hashMap.put("a", "b");
        System.out.println(hashMap.get("a"));
    }
}
