# Docker 命令记录

## 构建镜像

```bash
docker build -t qianguanguan:latest .
```

## 导出镜像

```bash
docker save -o qianguanguan.tar qianguanguan:latest
```

## 导入镜像

```bash
docker load -i qianguanguan.tar
```

## 启动服务

```bash
docker compose up -d
```
