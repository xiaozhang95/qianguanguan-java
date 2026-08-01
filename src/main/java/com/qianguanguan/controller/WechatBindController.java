package com.qianguanguan.controller;

import com.qianguanguan.constant.JwtClaims;
import com.qianguanguan.service.WechatBindService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 微信绑定模块控制器
 * <p>
 * 已登录用户在 Web 端扫码，将微信小程序账号与当前系统账号绑定。
 * 流程：生成绑定场景码 → 用户扫码 → 小程序确认 → Web 轮询状态
 */
// Swagger 分组
@Tag(name = "微信绑定模块", description = "网页端扫码绑定微信相关接口")
// REST 控制器
@RestController
// 统一基础路径
@RequestMapping("/api/business")
// 自动注入 final 字段
@RequiredArgsConstructor
public class WechatBindController {

    // --- 通过构造器注入微信绑定服务 ---
    private final WechatBindService wechatBindService;

    @Operation(summary = "生成绑定场景码", description = "生成扫码绑定的场景ID和二维码")
    // GET /api/business/wechat-bind/scene
    @GetMapping("/wechat-bind/scene")
    public Map<String, String> generateScene(
            // 获取当前登录用户 ID
            HttpServletRequest request
    ) {
        // 从JWT过滤器写入的Request属性中取出用户ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托Service生成绑定场景码，返回 {sceneId, qrImage}
        return wechatBindService.generateScene(userId);
    }

    @Operation(summary = "查询绑定场景码状态", description = "轮询场景码是否被确认")
    // GET /api/business/wechat-bind/status?sceneId=xxx
    @GetMapping("/wechat-bind/status")
    public Map<String, Object> checkStatus(
            // URL 参数：场景码 ID
            @RequestParam String sceneId
    ) {
        // 委托Service查询绑定状态，供Web端轮询
        return wechatBindService.checkStatus(sceneId);
    }

    @Operation(summary = "确认绑定", description = "小程序确认绑定微信")
    // POST /api/business/wechat-bind/confirm
    @PostMapping("/wechat-bind/confirm")
    public Map<String, Object> confirmBind(
            // 请求体：{sceneId, code}
            @RequestBody Map<String, String> body
    ) {
        // 从请求体中取出场景码 ID
        String sceneId = body.get("sceneId");
        // 从请求体中取出微信临时登录凭证 code
        String code = body.get("code");
        // 委托Service用code换OpenID并绑定到当前用户
        return wechatBindService.confirmBind(sceneId, code);
    }
}
