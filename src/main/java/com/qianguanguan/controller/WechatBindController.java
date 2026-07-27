package com.qianguanguan.controller;

import com.qianguanguan.constant.JwtClaims;
import com.qianguanguan.service.WechatBindService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "微信绑定模块", description = "网页端扫码绑定微信相关接口")
@RestController
@RequestMapping("/api/business")
@RequiredArgsConstructor
public class WechatBindController {

    private final WechatBindService wechatBindService;

    @Operation(summary = "生成绑定场景码", description = "生成扫码绑定的场景ID和二维码")
    @GetMapping("/wechat-bind/scene")
    public Map<String, String> generateScene(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        return wechatBindService.generateScene(userId);
    }

    @Operation(summary = "查询绑定场景码状态", description = "轮询场景码是否被确认")
    @GetMapping("/wechat-bind/status")
    public Map<String, Object> checkStatus(@RequestParam String sceneId) {
        return wechatBindService.checkStatus(sceneId);
    }

    @Operation(summary = "确认绑定", description = "小程序确认绑定微信")
    @PostMapping("/wechat-bind/confirm")
    public Map<String, Object> confirmBind(@RequestBody Map<String, String> body) {
        String sceneId = body.get("sceneId");
        String code = body.get("code");
        return wechatBindService.confirmBind(sceneId, code);
    }
}
