package com.wen.thumbsystembackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/thumb")
@Tag(name = "点赞管理接口")
public class ThumbController {
}
