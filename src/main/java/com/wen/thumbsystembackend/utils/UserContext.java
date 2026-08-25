package com.wen.thumbsystembackend.utils;

import com.wen.thumbsystembackend.entity.User;

public class UserContext {
    private static ThreadLocal<User> userHolder = new ThreadLocal<User>();

    public static User getUser() {
        return userHolder.get();
    }
    public static void setUser(User user) {
        userHolder.set(user);
    }

    public static void removeUser() {
        userHolder.remove();
    }

}
