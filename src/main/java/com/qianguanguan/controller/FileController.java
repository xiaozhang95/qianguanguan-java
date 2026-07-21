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

@Tag(name = "文件上传", description = "文件上传相关接口")
@RestController
@RequestMapping("/api/business")
public class FileController {

    @Value("${file.upload-dir:#{systemProperties['user.dir'] + '/upload'}}")
    private String uploadDir;

    @Operation(summary = "上传文件", description = "上传文件并返回访问路径")
    @PostMapping("/upload")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        Path dir = Paths.get(uploadDir);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        String ext = file.getOriginalFilename() != null
                && file.getOriginalFilename().contains(".")
                ? file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."))
                : ".png";
        String filename = UUID.randomUUID() + ext;
        File dest = dir.resolve(filename).toFile();
        file.transferTo(dest);
        return Map.of("url", "/static/" + filename);
    }
}
