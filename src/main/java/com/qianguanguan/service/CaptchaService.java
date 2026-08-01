package com.qianguanguan.service;

import com.qianguanguan.model.CaptchaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 图形验证码服务
 * <p>
 * 生成 4 位字符验证码（排除 I/O/l/0/1 等易混淆字符），
 * 绘制为带干扰线和噪点的 PNG 图片，base64 编码后返回。
 * 验证码存入 Redis，5 分钟有效，校验成功后立即删除。
 */
// Spring Service 组件注解
@Service
// 自动生成构造器注入
@RequiredArgsConstructor
public class CaptchaService {

    /** Redis key 前缀：captcha:{uuid} */
    private static final String CAPTCHA_PREFIX = "captcha:";
    /** 验证码有效期：5 分钟 */
    private static final long CAPTCHA_EXPIRE = 5;
    /** 验证码字符池：排除易混淆字符 I、O、l、0、1 */
    private static final String CHAR_POOL =
            "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    /** 图片宽度：130px */
    private static final int WIDTH = 130;
    /** 图片高度：48px */
    private static final int HEIGHT = 48;
    /** 验证码长度：4 位 */
    private static final int CODE_LENGTH = 4;

    // --- 依赖注入 ---
    // Redis 操作
    private final StringRedisTemplate stringRedisTemplate;
    // 安全随机数生成器
    private final SecureRandom random = new SecureRandom();

    /**
     * 生成图形验证码
     * <p>
     * 1. 生成 UUID 作为验证码 key（返回给前端，登录时回传）
     * 2. 随机生成 4 位验证码字符串
     * 3. 将验证码存入 Redis（key = captcha:{uuid}，5 分钟过期）
     * 4. 绘制干扰图并转为 base64 字符串
     *
     * @return CaptchaResponse（captchaKey + base64 图片）
     */
    public CaptchaResponse generate() {
        // --- 1. 生成验证码 key（UUID） ---
        // 生成 UUID 作为 key，全局唯一
        String captchaKey = UUID.randomUUID().toString();
        // --- 2. 随机生成验证码字符串 ---
        // 生成 4 位验证码，如 "A3fG"
        String code = generateCode();

        // --- 3. 存入 Redis，5 分钟有效 ---
        // 写入 Redis 字符串缓存
        stringRedisTemplate.opsForValue().set(
                // Key: captcha:{uuid}
                CAPTCHA_PREFIX + captchaKey,
                // Value: 验证码字符串
                code,
                // 超时时间
                CAPTCHA_EXPIRE,
                // 时间单位：分钟
                TimeUnit.MINUTES
        );

        // --- 4. 生成 base64 编码的验证码图片 ---
        // 绘制验证码图片并 Base64 编码
        String base64Image = generateImage(code);

        // --- 5. 构建响应：key + data:image/png;base64,... ---
        return CaptchaResponse.builder()
                // 前端登录时需回传
                .captchaKey(captchaKey)
                // 可直接用于 <img src="...">
                .captchaImage("data:image/png;base64," + base64Image)
                .build();
    }

    /**
     * 校验图形验证码
     * <p>
     * 不区分大小写，校验成功后立即删除 Redis 中的验证码（一次性使用）。
     *
     * @param captchaKey  验证码 key
     * @param captchaCode 用户输入的验证码
     * @return true-校验通过 / false-校验失败
     */
    public boolean validate(String captchaKey, String captchaCode) {
        // 任一参数为空则直接校验失败
        if (captchaKey == null || captchaCode == null) {
            // 校验失败
            return false;
        }
        // 拼接完整 Redis key
        String key = CAPTCHA_PREFIX + captchaKey;
        // 读取 Redis 中存储的验证码
        String storedCode = stringRedisTemplate.opsForValue().get(key);
        // 未取到：验证码不存在或已过期
        if (storedCode == null) {
            // 校验失败
            return false;
        }
        // 立即删除，验证码一次性有效（防重放）
        stringRedisTemplate.delete(key);
        // 忽略大小写比对，容忍输入误差
        return storedCode.equalsIgnoreCase(captchaCode);
    }

    /**
     * 随机生成验证码字符串
     * <p>
     * 从字符池中随机选取 CODE_LENGTH（4）个字符拼接成字符串。
     *
     * @return 4 位随机字符串
     */
    private String generateCode() {
        // 按长度预分配容量，避免动态扩容
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        // 循环生成 CODE_LENGTH 个字符
        for (int i = 0; i < CODE_LENGTH; i++) {
            // 从字符池随机取一个字符
            sb.append(CHAR_POOL.charAt(random.nextInt(CHAR_POOL.length())));
        }
        // 返回拼接好的验证码字符串
        return sb.toString();
    }

    /**
     * 将验证码绘制为带干扰的 PNG 图片，返回 base64 编码字符串
     * <p>
     * 绘制内容包括：
     * <ul>
     *   <li>浅灰背景</li>
     *   <li>4 个随机颜色、随机旋转角度的字符</li>
     *   <li>5 条随机干扰线</li>
     *   <li>30 个随机噪点</li>
     * </ul>
     *
     * @param code 验证码字符串
     * @return PNG 图片的 base64 字符串
     */
    private String generateImage(String code) {
        // --- 1. 创建画布 ---
        // 创建 RGB 类型画布
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        // 获取 2D 绘图上下文
        Graphics2D g2d = image.createGraphics();

        // --- 2. 绘制背景 ---
        // 浅灰色背景
        g2d.setColor(new Color(240, 240, 240));
        // 填充整个画布
        g2d.fillRect(0, 0, WIDTH, HEIGHT);

        // --- 3. 设置字体 ---
        // 粗体 30 号 Arial，便于识别
        g2d.setFont(new Font("Arial", Font.BOLD, 30));

        // --- 4. 逐个绘制字符（带随机颜色和旋转） ---
        // 遍历每个验证码字符
        for (int i = 0; i < CODE_LENGTH; i++) {
            // 取第 i 个字符
            String ch = String.valueOf(code.charAt(i));
            // 设置字符颜色
            g2d.setColor(new Color(
                    // RGB 均为深色随机值
                    random.nextInt(100), random.nextInt(100), random.nextInt(100)));
            // 水平位置：递增基准 + 随机偏移
            int x = 15 + i * 28 + random.nextInt(8);
            // 垂直位置：基准 + 随机偏移
            int y = 28 + random.nextInt(10);
            // 随机旋转角：-0.25 ~ 0.25 弧度
            double angle = (random.nextDouble() - 0.5) * 0.5;
            // 以绘制坐标 (x,y) 为原点旋转坐标系
            g2d.rotate(angle, x, y);
            // 绘制字符
            g2d.drawString(ch, x, y);
            // 反向旋转，恢复坐标系
            g2d.rotate(-angle, x, y);
        }

        // --- 5. 绘制干扰线（5 条） ---
        // 循环绘制 5 条干扰线
        for (int i = 0; i < 5; i++) {
            // 设置干扰线颜色
            g2d.setColor(new Color(
                    // RGB 均为随机浅色
                    random.nextInt(200), random.nextInt(200), random.nextInt(200)));
            // 绘制一条随机干扰线
            g2d.drawLine(
                    // 起点（随机坐标）
                    random.nextInt(WIDTH), random.nextInt(HEIGHT),
                    // 终点（随机坐标）
                    random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }

        // --- 6. 绘制噪点（30 个） ---
        // 循环绘制 30 个噪点
        for (int i = 0; i < 30; i++) {
            // 设置噪点颜色
            g2d.setColor(new Color(
                    // RGB 均为随机浅色
                    random.nextInt(200), random.nextInt(200), random.nextInt(200)));
            // 在随机位置画 2x2 椭圆噪点
            g2d.fillOval(random.nextInt(WIDTH), random.nextInt(HEIGHT), 2, 2);
        }

        // --- 7. 释放图形资源 ---
        // 释放绘图资源，防止内存泄漏
        g2d.dispose();

        // --- 8. 将 BufferedImage 编码为 PNG 字节，再 Base64 编码 ---
        // try-with-resources 自动关闭流
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // 按 PNG 格式写入字节流
            ImageIO.write(image, "png", baos);
            // 字节数组转 Base64 字符串
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            // 编码异常时抛业务异常
            throw new RuntimeException("生成验证码图片失败", e);
        }
    }
}
