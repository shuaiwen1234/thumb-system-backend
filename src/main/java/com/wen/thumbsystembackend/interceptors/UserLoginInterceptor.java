package com.wen.thumbsystembackend.interceptors;

import cn.hutool.core.util.StrUtil;
import com.wen.thumbsystembackend.entity.User;
import com.wen.thumbsystembackend.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class UserLoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String userIdStr = request.getHeader("user");
        if(StrUtil.isNotBlank(userIdStr)){
            User user = new User();
            user.setUserId(Long.valueOf(userIdStr));
            UserContext.setUser(user);
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
