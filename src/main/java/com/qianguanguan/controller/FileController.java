package com.qianguanguan.controller;

// --- Swagger 注解 ---
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
// --- Spring 注解：从配置文件注入值 ---
import org.springframework.beans.factory.annotation.Value;
// --- Spring MVC 注解 ---
import org.springframework.web.bind.annotation.*;
// --- Spring 文件上传接口 ---
import org.springframework.web.multipart.MultipartFile;

// --- Java IO / NIO ---
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传控制器
 * <p>
 * 支持单文件上传，文件以 UUID 重命名保存到本地 upload 目录，
 * 返回可访问的 URL 路径。
 */
@Tag(name = "文件上传", description = "文件上传相关接口") // Swagger 分组
@RestController                 // REST 控制器
@RequestMapping("/api/business") // 统一基础路径
public class FileController {

    /**
     * 从配置文件读取上传目录路径
     * 默认值为：当前工作目录下的 upload 文件夹
     * ${file.upload-dir} → 配置文件中的值
     * #{systemProperties['user.dir'] + '/upload'} → SpEL 表达式默认值
     */
    @Value("${file.upload-dir:#{systemProperties['user.dir'] + '/upload'}}")
    private String uploadDir;

    @Operation(summary = "上传文件", description = "上传文件并返回访问路径")
    @PostMapping("/upload")  // POST /api/business/upload
    public Map<String, String> upload(
            @RequestParam("file") MultipartFile file // 表单参数名为 "file"
    ) throws IOException {
        // --- 1. 确保上传目录存在 ---
        Path dir = Paths.get(uploadDir);           // 将路径字符串转为 Path 对象
        if (!Files.exists(dir)) {                  // 检查目录是否存在
            Files.createDirectories(dir);          // 不存在则递归创建
        }

        // --- 2. 提取文件扩展名 ---
        String ext = file.getOriginalFilename() != null
                && file.getOriginalFilename().contains(".")  // 原始文件名存在且含点号
                ? file.getOriginalFilename()
                        .substring(file.getOriginalFilename().lastIndexOf(".")) // 截取最后一个 . 之后的部分
                : ".png"; // 无法识别扩展名时默认使用 .png

        // --- 3. UUID 重命名，防止文件名冲突 ---
        String filename = UUID.randomUUID() + ext;   // 生成唯一文件名，如 "a1b2c3d4.jpg"
        File dest = dir.resolve(filename).toFile();  // 拼接完整目标路径

        // --- 4. 保存文件到磁盘 ---
        file.transferTo(dest);                       // Spring MultipartFile 提供的方法

        // --- 5. 返回可访问的 URL ---
        // 配合 CorsConfig 中的静态资源映射，/upload/xxx 可直接访问
        return Map.of("url", "/upload/" + filename);
    }
}
