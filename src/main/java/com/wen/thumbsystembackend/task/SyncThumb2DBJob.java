package com.wen.thumbsystembackend.task;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.wen.thumbsystembackend.mapper.BlogMapper;
import com.wen.thumbsystembackend.mapper.ThumbMapper;
import com.wen.thumbsystembackend.service.ThumbService;
import com.wen.thumbsystembackend.utils.RedisKeyUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 定时将 Redis 中的临时点赞数据同步到数据库  
 * @author zhangziwen
 */  
@Component
@Slf4j
public class SyncThumb2DBJob {  
  
    @Resource
    private ThumbService thumbService;
  
    @Resource  
    private BlogMapper blogMapper;
  
    @Resource  
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private ThumbMapper  thumbMapper;

    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(5,8,30L, TimeUnit.SECONDS,new ArrayBlockingQueue<>(1000), Executors.defaultThreadFactory(),new ThreadPoolExecutor.AbortPolicy());


    //每分钟执行一次
    @Scheduled(fixedRate = 1000*60)
    @Transactional(rollbackFor = Exception.class)
    public void run() {  
        log.info("开始执行");
        //DateUtil是HuTool包里的一个工具类
        //DateUtil.date()是获取当前时间的date对象
        DateTime nowDate = DateUtil.date();
        DateTime dateTime = DateUtil.offsetMinute(nowDate, -1);
        String date = DateUtil.format(dateTime, "yyyyMMddHH:mm");
        syncThumb2DBByDate(date);
        log.info("临时数据同步完成");
    }  
  
    public void syncThumb2DBByDate(String date) {
        //先对每条博客的点赞数量进行更新
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(date);
        //这个entries里存的是 tempThumbKey(如 thumb:temp:{时间})这个key对应的每一个field和与其对应的value(userId:blogId和status)
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(tempThumbKey);
        if(entries.isEmpty()){
            return;
        }
        //这里的这个map的key不用在意 随意取的 value是个map(redis hash的field和value) 他的key是String(如userId,blogId,status) value是对应的值(如1,2,3)
        Map<String, Map<String, Long>> noKeyMap = entries.entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey().toString(),
                entry -> {
                    //内层的map
                    String key = entry.getKey().toString();
                    String[] keys = key.split(":");
                    String userIdStr = keys[0];
                    String blogIdStr = keys[1];
                    String statusStr = entry.getValue().toString();

                    Long userId = Long.valueOf(userIdStr);
                    Long blogId = Long.valueOf(blogIdStr);
                    Long status = Long.valueOf(statusStr);
                    Map<String, Long> map = new HashMap<>(3);
                    map.put("userId", userId);
                    map.put("blogId", blogId);
                    map.put("status", status);
                    return map;
                }
        ));

        //这里的Long是blogId List<Map<String, Integer>>>这里的map就是noKeyMap里的多个value放在一起
        Map<Long, List<Map<String, Long>>> blogIdAndList = noKeyMap.values().stream().collect(Collectors.groupingBy(valueMap -> valueMap.get("blogId")));
        //这里的这个blogIdAndThumbNumChanged里就是每个blogId和与之对应的点赞变化数
        Map<Long, Long> blogIdAndThumbNumChanged = blogIdAndList.entrySet().stream().collect(Collectors.toMap(
                entry -> Long.valueOf(entry.getKey()),
                entry -> {
                    List<Map<String, Long>> list = entry.getValue();
                    int status = 0;
                    for (Map<String, Long> map : list) {
                        status += map.get("status");
                    }
                    return Long.valueOf(status);
                }
        ));
        //更新博客的点赞数
        blogMapper.batchUpdateThumbCount(blogIdAndThumbNumChanged);

        //再更新点赞记录表

        //先对noKeyMap里的数据根据userId和blogId进行分组 在对组里的value进行累加 判断操作的种类(点赞 取消点赞 无有效操作)
        //分组(key是userId:blogId,value是这个用户的id，博客的id以及用户对这个博客进行的所有操作)
        //如 key: 1:3    value: (userId:1 blogId:3 value: -1),(userId:1 blogId:3 value: 1)
        Map<String, List<Map<String, Long>>> collect = noKeyMap.values().stream().collect(Collectors.groupingBy(valueMap -> valueMap.get("userId") + ":" + valueMap.get("blogId")));

        List<Map<String,Long>> thumbRecordMapList = new ArrayList<>();
        //进行累加
        for (List<Map<String, Long>> value : collect.values()) {
            Map<String, Long> map = new HashMap<>(3);
            map.put("userId", value.get(0).get("userId"));
            map.put("blogId", value.get(0).get("blogId"));
            Long status = 0L;
            for(Map<String, Long> map2 : value){
                status += map2.get("status");
            }
            map.put("status", status);
            thumbRecordMapList.add(map);
        }

        //插入点赞表
        List<Map<String, Long>> thumbAddList = thumbRecordMapList.stream().filter(map -> map.get("status") > 0).collect(Collectors.toList());
        if (!thumbAddList.isEmpty()) {
            thumbMapper.saveBatch(thumbAddList);
        }
        //从点赞表里删除
        List<Map<String, Long>> thumbDelList = thumbRecordMapList.stream().filter(map -> map.get("status") < 0).collect(Collectors.toList());
        if (!thumbDelList.isEmpty()) {
            thumbMapper.deleteBatch(thumbDelList);
        }

        //用线程池删除redis里的临时的数据(tempThumbKey 保存用户对某个博客的操作)
        // 等事务提交成功后再删临时 key；若事务回滚则不删，Redis 源数据保留，可被补偿/重试
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    EXECUTOR.execute(() -> redisTemplate.delete(tempThumbKey));
                }
            });
        } else {
            // 万一没有事务上下文(比如被别处直接调用)，立即删
            EXECUTOR.execute(() -> redisTemplate.delete(tempThumbKey));
        }
    }
}
