package com.wen.thumbsystembackend;

import cn.hutool.core.util.RandomUtil;
import com.wen.thumbsystembackend.entity.User;
import com.wen.thumbsystembackend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.annotation.Resource;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ThumbBackendApplicationTests {

    @Resource
    private UserService userService;

    @Resource
    private MockMvc mockMvc;

    /**
     * 对应老师的 testLoginAndExportSessionToCsv：
     * 你的鉴权是请求头 user=userId，所以"登录验证 + 导出 userId 供 JMeter 用"
     */
    @Test
    void testLoginAndExportUserHeaderToCsv() throws Exception {
        List<User> list = userService.list();

        try (PrintWriter writer = new PrintWriter(new FileWriter("user_header_output.csv", false))) {
            writer.println("userId,timestamp");

            for (User user : list) {
                long testUserId = user.getUserId();          // 对应老师 user.getId()

                // 第二段：MockMvc 打登录接口冒烟（你的路径带 /api 前缀）
                mockMvc.perform(get("/api/user/login")
                                .param("userId", String.valueOf(testUserId))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk());          // 老师的 Session 断言 → 换成 200 断言

                // 第四段：直接导出 userId（你的 JMeter header 值）
                writer.printf("%d,%s%n", testUserId, LocalDateTime.now());
                System.out.println(" 写入 CSV：" + testUserId);
            }
        }
    }

    /*@Test
    void addUser() {
        for (int i = 0; i < 50000; i++) {
            User user = new User();
            user.setUserName(RandomUtil.randomString(6));
            userService.save(user);
        }
    }*/

}