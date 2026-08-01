package com.qianguanguan.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
// Swagger 分组
@Tag(name = "文件上传", description = "文件上传相关接口")
// REST 控制器
@RestController
// 统一基础路径
@RequestMapping("/api/business")
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
    // POST /api/business/upload
    @PostMapping("/upload")
    public Map<String, String> upload(
            // 表单参数名为 "file"
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        // --- 1. 确保上传目录存在 ---
        // 将配置的上传路径字符串转为 Path 对象
        Path dir = Paths.get(uploadDir);
        // 目录不存在时才需要创建
        if (!Files.exists(dir)) {
            // 递归创建目录及其父目录
            Files.createDirectories(dir);
        }

        // --- 2. 提取文件扩展名 ---
        // 原始文件名非空才继续解析
        String ext = file.getOriginalFilename() != null
                // 文件名含点号才能截取到扩展名
                && file.getOriginalFilename().contains(".")
                // 含扩展名时取原文件名（下方再截取扩展名）
                ? file.getOriginalFilename()
                        // 从最后一个点号截取到末尾，得到扩展名
                        .substring(file.getOriginalFilename().lastIndexOf("."))
                // 无法识别扩展名时默认使用 .png
                : ".png";

        // --- 3. UUID 重命名，防止文件名冲突 ---
        // UUID 拼接扩展名，生成全局唯一的文件名
        String filename = UUID.randomUUID() + ext;
        // 在上传目录下解析出完整的目标文件
        File dest = dir.resolve(filename).toFile();

        // --- 4. 保存文件到磁盘 ---
        // 调用 Spring 方法将上传文件写入磁盘目标位置
        file.transferTo(dest);

        // --- 5. 返回可访问的 URL ---
        // 配合 CorsConfig 的静态资源映射，/upload/xxx 可直接访问
        return Map.of("url", "/upload/" + filename);
    }
}
