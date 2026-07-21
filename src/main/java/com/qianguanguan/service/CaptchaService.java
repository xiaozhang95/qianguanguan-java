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

@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String CAPTCHA_PREFIX = "captcha:";
    private static final long CAPTCHA_EXPIRE = 5;
    private static final String CHAR_POOL = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int WIDTH = 130;
    private static final int HEIGHT = 48;
    private static final int CODE_LENGTH = 4;

    private final StringRedisTemplate stringRedisTemplate;
    private final SecureRandom random = new SecureRandom();

    public CaptchaResponse generate() {
        String captchaKey = UUID.randomUUID().toString();
        String code = generateCode();

        stringRedisTemplate.opsForValue().set(
                CAPTCHA_PREFIX + captchaKey,
                code,
                CAPTCHA_EXPIRE,
                TimeUnit.MINUTES
        );

        String base64Image = generateImage(code);

        return CaptchaResponse.builder()
                .captchaKey(captchaKey)
                .captchaImage("data:image/png;base64," + base64Image)
                .build();
    }

    public boolean validate(String captchaKey, String captchaCode) {
        if (captchaKey == null || captchaCode == null) {
            return false;
        }
        String key = CAPTCHA_PREFIX + captchaKey;
        String storedCode = stringRedisTemplate.opsForValue().get(key);
        if (storedCode == null) {
            return false;
        }
        stringRedisTemplate.delete(key);
        return storedCode.equalsIgnoreCase(captchaCode);
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHAR_POOL.charAt(random.nextInt(CHAR_POOL.length())));
        }
        return sb.toString();
    }

    private String generateImage(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(new Color(240, 240, 240));
        g2d.fillRect(0, 0, WIDTH, HEIGHT);

        g2d.setFont(new Font("Arial", Font.BOLD, 30));

        for (int i = 0; i < CODE_LENGTH; i++) {
            String ch = String.valueOf(code.charAt(i));
            g2d.setColor(new Color(random.nextInt(100), random.nextInt(100), random.nextInt(100)));
            int x = 15 + i * 28 + random.nextInt(8);
            int y = 28 + random.nextInt(10);
            double angle = (random.nextDouble() - 0.5) * 0.5;
            g2d.rotate(angle, x, y);
            g2d.drawString(ch, x, y);
            g2d.rotate(-angle, x, y);
        }

        for (int i = 0; i < 5; i++) {
            g2d.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
            g2d.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                    random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }

        for (int i = 0; i < 30; i++) {
            g2d.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
            g2d.fillOval(random.nextInt(WIDTH), random.nextInt(HEIGHT), 2, 2);
        }

        g2d.dispose();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("生成验证码图片失败", e);
        }
    }
}
