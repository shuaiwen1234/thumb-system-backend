package com.wen.thumbsystembackend.manager.cache;


import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * @author zhangziwen
 * 契约：检测器要会哪些招
 */
public interface TopK {
 //记录一次访问 返回热度数据
 AddResult add(String key, int increment);
 //当前TopK榜单
 List<Item> list();
 //被挤出榜单的Item
 BlockingQueue<Item> expelled();
 //整体热度减半(降温)
 void fading();
 //总计数 记录的是所有流经他(有代码调用了hotKeyDetector.add(key, 1))的访问次数的总和
 long total();
}
