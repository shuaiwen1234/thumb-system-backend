package com.wen.thumbsystembackend.interceptors;

import cn.hutool.core.util.StrUtil;
import com.wen.thumbsystembackend.entity.User;
import com.wen.thumbsystembackend.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class UserLoginInterceptor implements HandlerInterceptor {

    /**
     * 访问查询单个博客的URI
     * 如/api/blog
     */
    @Value("${blog.one}")
    private String one;
    /**
     * 访问查询博客列表的URI
     * 如/api/blog/list
     */
    @Value("${blog.list}")
    private String list;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String userIdStr = request.getHeader("User");
        if(StrUtil.isNotBlank(userIdStr)){
            User user = new User();
            user.setUserId(Long.valueOf(userIdStr));
            UserContext.setUser(user);
            return true;
        }
        if(request.getRequestURI().equals(one)||request.getRequestURI().equals(list)){
            //如果访问的是查看博客或者博客列表 未登录也放行(如果直接在配置类里把这个路径排除了就会导致登陆的用户也无法获取到用户信息)
            return true;
        }
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        UserContext.removeUser();
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
