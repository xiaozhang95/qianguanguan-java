package com.qianguanguan.controller;

// --- JWT Claims 常量 ---
import com.qianguanguan.constant.JwtClaims;
// --- 微信绑定服务 ---
import com.qianguanguan.service.WechatBindService;
// --- Swagger 注解 ---
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
// --- Jakarta Servlet ---
import jakarta.servlet.http.HttpServletRequest;
// --- Lombok ---
import lombok.RequiredArgsConstructor;
// --- Spring MVC ---
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 微信绑定模块控制器
 * <p>
 * 已登录用户在 Web 端扫码，将微信小程序账号与当前系统账号绑定。
 * 流程：生成绑定场景码 → 用户扫码 → 小程序确认 → Web 轮询状态
 */
@Tag(name = "微信绑定模块", description = "网页端扫码绑定微信相关接口") // Swagger 分组
@RestController                 // REST 控制器
@RequestMapping("/api/business") // 统一基础路径
@RequiredArgsConstructor        // 自动注入 final 字段
public class WechatBindController {

    // --- 通过构造器注入微信绑定服务 ---
    private final WechatBindService wechatBindService;

    @Operation(summary = "生成绑定场景码", description = "生成扫码绑定的场景ID和二维码")
    @GetMapping("/wechat-bind/scene")  // GET /api/business/wechat-bind/scene
    public Map<String, String> generateScene(
            HttpServletRequest request // 获取当前登录用户 ID
    ) {
        // 从 Request 属性中取出当前登录用户 ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托 WechatBindService 生成绑定场景码，返回 {sceneId, qrImage}
        return wechatBindService.generateScene(userId);
    }

    @Operation(summary = "查询绑定场景码状态", description = "轮询场景码是否被确认")
    @GetMapping("/wechat-bind/status")  // GET /api/business/wechat-bind/status?sceneId=xxx
    public Map<String, Object> checkStatus(
            @RequestParam String sceneId // URL 参数：场景码 ID
    ) {
        // 委托 WechatBindService 查询绑定状态
        return wechatBindService.checkStatus(sceneId);
    }

    @Operation(summary = "确认绑定", description = "小程序确认绑定微信")
    @PostMapping("/wechat-bind/confirm")  // POST /api/business/wechat-bind/confirm
    public Map<String, Object> confirmBind(
            @RequestBody Map<String, String> body // 请求体：{sceneId, code}
    ) {
        // 从请求体中取出场景码 ID
        String sceneId = body.get("sceneId");
        // 从请求体中取出微信临时登录凭证 code
        String code = body.get("code");
        // 委托 WechatBindService 将微信 OpenID 绑定到当前用户
        return wechatBindService.confirmBind(sceneId, code);
    }
}
