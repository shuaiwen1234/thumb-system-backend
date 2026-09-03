package com.wen.thumbsystembackend.manager.cache;

import cn.hutool.core.util.HashUtil;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author zhangziwen
 */
public class HeavyKeeper implements TopK {
    /**
     * 衰减查表长度	预先把衰减概率算好 256 档，用的时候直接查，不现场算
     */
    private static final int LOOKUP_TABLE_SIZE = 256;
    /**
     * TopK榜单名额上限
     */
    private final int k;
    /**
     * 二维桶数组的宽度(即每行有多少个桶)
     * 这里的桶里装的是Node对象
     * 桶 = 指纹 + 计数
     */
    private final int width;
    /**
     * 二维桶数组的深度(一共有多少行)
     */
    private final int depth;
    /**
     * 缓存的是每个热度对应的抢桶概率 热度越高这个概率越小 桶越不容易被抢走
     * lookupTable[i] = decay^i(decay的i次方): 缓存"抢桶成功率"
     * 对方热度为 i 时，我出拳命中的概率 = 0.92^i
     * 热度 i 越高 → 概率指数衰减 → 热桶越难被抢走
     */
    private final double[] lookupTable;
    /**
     * 二维桶数组(即热度账本 用来保存所有key的计数分布)
     */
    private final Bucket[][] buckets;
    /**
     * 最小堆(即当前TopK的榜单 用来保存TopK里K个Node)
     * 这里的PriorityQueue是优先级队列 即每次出队元素都是队列中优先级最高或者最低的元素
     */
    private final PriorityQueue<Node> minHeap;
    /**
     * 存放从TopK榜单里被移除的Node 谁下榜谁进队
     */
    private final BlockingQueue<Item> expelledQueue;
    /**
     * 骰子 发生Hash冲突时靠random.nextDouble()（0~1 均匀随机）和 decay 比较，决定能不能抢对方地盘
     */
    private final Random random;
    /**
     * 总记录数 即整个检测器见到的流量的总和 fading直接减半
     */
    private long total;
    /**
     * 进入TopK榜单的资格 出现几次才记录
     */
    private final int minCount;

    public HeavyKeeper(int k, int width, int depth, double decay, int minCount) {
        this.k = k;
        this.width = width;
        this.depth = depth;
        this.minCount = minCount;

        this.lookupTable = new double[LOOKUP_TABLE_SIZE];
        for (int i = 0; i < LOOKUP_TABLE_SIZE; i++) {
            //这里计算的是decay的i次方
            lookupTable[i] = Math.pow(decay, i);
        }

        this.buckets = new Bucket[depth][width];
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < width; j++) {
                buckets[i][j] = new Bucket();
            }
        }

        //这个优先级队列按Node.count从小到大排序 即Node.count越小 优先级越高 弹出元素时越先弹出
        this.minHeap = new PriorityQueue<>(Comparator.comparingInt(n -> n.count));
        this.expelledQueue = new LinkedBlockingQueue<>();
        this.random = new Random();
        this.total = 0;
    }
    @Override
    public AddResult add(String key, int increment) {
        byte[] keyBytes = key.getBytes();
        //把这个key的字节数组转为hash 计算出桶内的指纹
        long itemFingerprint = hash(keyBytes);
        //当前key的峰值热度 即最大热度
        int maxCount = 0;

        for (int i = 0; i < depth; i++) {
            //计算出这个key在hash桶数组里的下标(即数组下标)
            int bucketNumber = Math.abs(hash(keyBytes)+i) % width;
            //取出这个桶
            Bucket bucket = buckets[i][bucketNumber];

            synchronized (bucket) {
                if (bucket.count == 0) {
                    //此时是空桶
                    bucket.fingerprint = itemFingerprint;
                    bucket.count = increment;
                    maxCount = Math.max(maxCount, increment);
                } else if (bucket.fingerprint == itemFingerprint) {
                    //找到了他所在的桶
                    bucket.count += increment;
                    maxCount = Math.max(maxCount, bucket.count);
                } else {
                    //发生了哈希冲突 找到的是别人的桶 通过概率衰减抢地盘
                    for (int j = 0; j < increment; j++) {
                        double decay = bucket.count < LOOKUP_TABLE_SIZE ?
                                lookupTable[bucket.count] :
                                lookupTable[LOOKUP_TABLE_SIZE - 1]; //lookupTable里之缓存了热度最高到255的概率 热度大于255统一按255算
                        if (random.nextDouble() < decay) {  //掷骰子 并且赢了
                            bucket.count--; //对方的对应热度-1
                            if (bucket.count == 0) {  //热度为零时则抢地盘
                                bucket.fingerprint = itemFingerprint;
                                bucket.count = increment - j;  //初始count为剩余增量
                                maxCount = Math.max(maxCount, bucket.count);
                                break;
                            }
                        }
                    }
                }
            }
        }

        //总记录数增加对应值
        total += increment;

        if (maxCount < minCount) {
            //最大热度小于上榜的最低热度 不上榜
            return new AddResult(null, false, null);
        }

        //此时具备上榜资格 维护最小堆
        synchronized (minHeap) {
            boolean isHot = false;
            String expelled = null;
            //判断自己是否在榜上
            Optional<Node> existing = minHeap.stream()
                    .filter(n -> n.key.equals(key))
                    .findFirst();

            if (existing.isPresent()) {
                //在榜上 先把自己移除 再把自己加入 实现更新
                minHeap.remove(existing.get());
                minHeap.add(new Node(key, maxCount));
                isHot = true;
            } else {
                //自己不在榜上 看看能不能给自己挤上榜
                //minHeap.peek()是查看队列上下一个要被弹出的数据
                if (minHeap.size() < k || maxCount >= Objects.requireNonNull(minHeap.peek()).count) {
                    //如果最小堆还没满或者最小堆里的最小的Item.count比当前的小就可以入榜(即加入最小堆)
                    Node newNode = new Node(key, maxCount);
                    if (minHeap.size() >= k) {
                        //此时最小堆里的Node达到了K个 弹出Node.count最小的那个 即优先级最高的
                        expelled = minHeap.poll().key;
                        expelledQueue.offer(new Item(expelled, maxCount));
                    }
                    //此时为最小堆里的Node个数还没到K个
                    minHeap.add(newNode);
                    isHot = true;
                }
                //无法上榜
            }

            return new AddResult(expelled, isHot, key);
        }
    }


    @Override
    public List<Item> list() {
        synchronized (minHeap) {
            List<Item> result = new ArrayList<>(minHeap.size());
            for (Node node : minHeap) {
                //把Node包装成Item对外展示
                result.add(new Item(node.key, node.count));
            }
            //按count(即热度)由大到小排序
            result.sort((a, b) -> Integer.compare(b.count(), a.count()));
            return result;
        }
    }

    @Override
    public BlockingQueue<Item> expelled() {
        return expelledQueue;
    }

    @Override
    public void fading() {
        for (Bucket[] row : buckets) {
            for (Bucket bucket : row) {
                synchronized (bucket) {
                    bucket.count = bucket.count >> 1;
                }
            }
        }

        synchronized (minHeap) {
            PriorityQueue<Node> newHeap = new PriorityQueue<>(Comparator.comparingInt(n -> n.count));
            for (Node node : minHeap) {
                newHeap.add(new Node(node.key, node.count >> 1));
            }
            //先清空原最小堆
            minHeap.clear();
            //再把更新后的数据添加到最小堆中 实现更细(热度减半)
            minHeap.addAll(newHeap);
        }

        total = total >> 1;
    }

    @Override
    public long total() {
        return total;
    }

    private static class Bucket {
        //指纹 记录的是当前key的Hash值
        //因为完整的key占内存 所以把key压缩成Hash来节省内存 Hash相同≈key相同
        long fingerprint;
        //计数 用来记录这个桶里的Key被记录的多少次热度 可以约等于热度
        //最小堆里的Node.count 就是对他的拷贝
        int count;
    }

    private static class Node {
        final String key;
        final int count;

        Node(String key, int count) {
            this.key = key;
            this.count = count;
        }
    }

    private static int hash(byte[] data) {
        return HashUtil.murmur32(data);
    }
}