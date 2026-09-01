package com.wen.thumbsystembackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wen.thumbsystembackend.entity.Thumb;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ThumbMapper extends BaseMapper<Thumb> {
    void saveBatch(@Param("list") List<Map<String, Long>> thumbAddList);

    void deleteBatch(@Param("list") List<Map<String, Long>> thumbDelList);
}
