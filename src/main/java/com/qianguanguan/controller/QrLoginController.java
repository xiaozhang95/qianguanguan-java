package com.qianguanguan.controller;

import com.qianguanguan.model.LoginResponse;
import com.qianguanguan.service.QrLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 微信扫码登录接口
 * <p>
 * 实现 Web 页面展示场景码 → 小程序输入场景码确认 → Web 自动登录 的跨端登录流程
 */
@Tag(name = "二维码登录模块", description = "微信扫码登录相关接口")
@RestController
@RequestMapping("/api/business")
@RequiredArgsConstructor
public class QrLoginController {

    private final QrLoginService qrLoginService;

    /**
     * 生成登录场景码
     * Web 页面调用，获取一个 16 位场景ID，存入 Redis 并开始轮询
     */
    @Operation(summary = "生成登录场景码", description = "生成扫码登录的场景ID")
    @GetMapping("/qr-login/scene")
    public Map<String, String> generateScene() {
        return qrLoginService.generateScene();
    }

    /**
     * 查询场景码状态
     * Web 页面每 2 秒轮询一次，检测小程序是否已确认登录
     */
    @Operation(summary = "查询场景码状态", description = "轮询场景码是否被确认")
    @GetMapping("/qr-login/status")
    public Map<String, Object> checkStatus(@RequestParam String sceneId) {
        return qrLoginService.checkStatus(sceneId);
    }

    /**
     * 确认登录
     * 小程序调用，传入场景ID + wx.login() 获取的 code，完成微信绑定和 JWT 签发
     */
    @Operation(summary = "确认登录", description = "小程序确认登录")
    @PostMapping("/qr-login/confirm")
    public LoginResponse confirmLogin(@RequestBody Map<String, String> body) {
        String sceneId = body.get("sceneId");
        String code = body.get("code");
        return qrLoginService.confirmLogin(sceneId, code);
    }
}
