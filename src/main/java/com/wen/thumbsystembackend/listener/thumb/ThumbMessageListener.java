package com.wen.thumbsystembackend.listener.thumb;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wen.thumbsystembackend.constant.MqConstant;
import com.wen.thumbsystembackend.entity.Blog;
import com.wen.thumbsystembackend.entity.Thumb;
import com.wen.thumbsystembackend.mapper.BlogMapper;
import com.wen.thumbsystembackend.mapper.ThumbMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 监听点赞信息
 */
@Component
@Slf4j
public class ThumbMessageListener {
    @Autowired
    private BlogMapper blogMapper;
    @Autowired
    private ThumbMapper thumbMapper;
    @Autowired
    private ConnectionFactory connectionFactory;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MqConstant.THUMB_DOTHUMB_QUEUE_NAME,durable = "true"),
            exchange = @Exchange(name = MqConstant.THUMB_EXCHANGE_NAME,type = "direct",durable = "true"),
            key = MqConstant.THUMB_DOTHUMB_BINDINGKEY
    ), containerFactory = "batchQueueTaskListenerContainerFactory")
    public void onThumbMessages(List<ThumbEvent> thumbEventList) {
        thumbMapper.insertIgnoreThumbEventList(thumbEventList);
        Map<Long, Long> map = thumbEventList.stream().collect(Collectors.groupingBy(thumb -> thumb.getBlogId(), Collectors.counting()));

        //更新博客的点赞数
        blogMapper.addThumbCountByMap(map);

    }
}
