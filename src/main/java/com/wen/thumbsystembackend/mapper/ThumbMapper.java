package com.wen.thumbsystembackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wen.thumbsystembackend.entity.Thumb;
import com.wen.thumbsystembackend.listener.thumb.ThumbEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ThumbMapper extends BaseMapper<Thumb> {
    void saveBatch(@Param("list") List<Map<String, Long>> thumbAddList);

    void deleteBatch(@Param("list") List<Map<String, Long>> thumbDelList);

    long insertIgnoreThumbEventList(@Param("list")List<ThumbEvent> list);

    long deleteByThumbEventList(@Param("list")List<ThumbEvent> list);
}
