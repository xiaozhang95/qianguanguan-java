package com.qianguanguan.service;

// --- 验证码响应模型 ---
import com.qianguanguan.model.CaptchaResponse;
// --- Lombok ---
import lombok.RequiredArgsConstructor;
// --- Redis ---
import org.springframework.data.redis.core.StringRedisTemplate;
// --- Spring ---
import org.springframework.stereotype.Service;

// --- Java AWT：绘制验证码图片 ---
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
@Service
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
    private final StringRedisTemplate stringRedisTemplate; // Redis 操作
    private final SecureRandom random = new SecureRandom(); // 安全随机数生成器

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
        String captchaKey = UUID.randomUUID().toString(); // 全局唯一标识
        // --- 2. 随机生成验证码字符串 ---
        String code = generateCode();                      // 如 "A3fG"

        // --- 3. 存入 Redis，5 分钟有效 ---
        stringRedisTemplate.opsForValue().set(
                CAPTCHA_PREFIX + captchaKey, // Key: captcha:{uuid}
                code,                        // Value: 验证码字符串
                CAPTCHA_EXPIRE,              // 超时时间
                TimeUnit.MINUTES             // 时间单位：分钟
        );

        // --- 4. 生成 base64 编码的验证码图片 ---
        String base64Image = generateImage(code);

        // --- 5. 构建响应：key + data:image/png;base64,... ---
        return CaptchaResponse.builder()
                .captchaKey(captchaKey)                             // 前端登录时需回传
                .captchaImage("data:image/png;base64," + base64Image) // 可直接用于 <img src="...">
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
        // 参数为空直接返回 false
        if (captchaKey == null || captchaCode == null) {
            return false;
        }
        // 从 Redis 取出存储的验证码
        String key = CAPTCHA_PREFIX + captchaKey;
        String storedCode = stringRedisTemplate.opsForValue().get(key);
        if (storedCode == null) {
            return false; // 验证码不存在或已过期
        }
        // 删除 Redis 中的验证码（一次性使用，防止重复提交）
        stringRedisTemplate.delete(key);
        // 不区分大小写比对
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
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            // 从字符池中随机取一个字符
            sb.append(CHAR_POOL.charAt(random.nextInt(CHAR_POOL.length())));
        }
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
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics(); // 获取 2D 绘图上下文

        // --- 2. 绘制背景 ---
        g2d.setColor(new Color(240, 240, 240)); // 浅灰色
        g2d.fillRect(0, 0, WIDTH, HEIGHT);      // 填充整个画布

        // --- 3. 设置字体 ---
        g2d.setFont(new Font("Arial", Font.BOLD, 30));

        // --- 4. 逐个绘制字符（带随机颜色和旋转） ---
        for (int i = 0; i < CODE_LENGTH; i++) {
            String ch = String.valueOf(code.charAt(i));         // 当前字符
            // 随机深色
            g2d.setColor(new Color(
                    random.nextInt(100), random.nextInt(100), random.nextInt(100)));
            // 随机位置（有偏移，让字符不完全对齐）
            int x = 15 + i * 28 + random.nextInt(8);            // 水平位置
            int y = 28 + random.nextInt(10);                    // 垂直位置
            // 随机旋转角度（-0.25 ~ 0.25 弧度）
            double angle = (random.nextDouble() - 0.5) * 0.5;
            g2d.rotate(angle, x, y);    // 旋转坐标系
            g2d.drawString(ch, x, y);   // 绘制字符
            g2d.rotate(-angle, x, y);   // 恢复坐标系
        }

        // --- 5. 绘制干扰线（5 条） ---
        for (int i = 0; i < 5; i++) {
            g2d.setColor(new Color(
                    random.nextInt(200), random.nextInt(200), random.nextInt(200)));
            g2d.drawLine(
                    random.nextInt(WIDTH), random.nextInt(HEIGHT),  // 起点
                    random.nextInt(WIDTH), random.nextInt(HEIGHT)); // 终点
        }

        // --- 6. 绘制噪点（30 个） ---
        for (int i = 0; i < 30; i++) {
            g2d.setColor(new Color(
                    random.nextInt(200), random.nextInt(200), random.nextInt(200)));
            g2d.fillOval(random.nextInt(WIDTH), random.nextInt(HEIGHT), 2, 2); // 2x2 椭圆
        }

        // --- 7. 释放图形资源 ---
        g2d.dispose();

        // --- 8. 将 BufferedImage 编码为 PNG 字节，再 Base64 编码 ---
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);            // 写入 PNG 到字节流
            return Base64.getEncoder().encodeToString(baos.toByteArray()); // Base64 编码
        } catch (Exception e) {
            throw new RuntimeException("生成验证码图片失败", e);
        }
    }
}
