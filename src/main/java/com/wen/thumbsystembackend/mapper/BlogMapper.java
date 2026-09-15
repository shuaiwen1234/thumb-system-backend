package com.wen.thumbsystembackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wen.thumbsystembackend.entity.Blog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface BlogMapper extends BaseMapper<Blog> {

    void batchUpdateThumbCount(@Param("map") Map<Long, Long> map);

    void addThumbCountByMap(@Param("map") Map<Long, Long> map);

    /**
     * 重新计算博客的点赞数(查点赞记录里的行数)
     * @param list 需要重新计算的博客的id
     */
    void recountThumbCount(@Param("list")List<Long> list);

    /**
     * 根据博客id查询出最新的博客id以及对应的点赞数
     * @param list 需要重新计算的博客的id
     * @return
     */
    List<Blog> getBlogIdAndThumbCount(@Param("list") List<Long> list);

    /**
     * 找出所有的漂移了的博客的id(点赞数和点赞表里的行数不一致)
     * @return
     */
    List<Long> findDriftedBlogIds();
}
