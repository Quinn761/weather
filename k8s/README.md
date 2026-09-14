# Weather Data Hub Kubernetes Deployment

This directory contains single-node Kubernetes manifests for K3s.

## Public URLs

- Frontend: `http://60.205.211.104:30000/`
- Backend health: `http://60.205.211.104:30080/api/health`

GitHub Actions 日常部署只构建前后端。SAM 2.1 的模型镜像需要独立部署，后续前后端发布会保留该服务。
仅在本机安装 SAM 不会让线上后端自动获得推理能力。

## AI 圈地线上排查

- Roboflow 返回 Cloudflare HTML 403：先记录响应中的 `CF-Ray`、发生时间和服务器出口 IP，
  联系 Roboflow 支持核查拦截策略。这种响应发生在获得推理 JSON 之前，增加超时时间无法解决。
- SAM 提示连接失败：检查 `kubectl -n weather get deploy,svc,pods` 中是否存在可用的
  `python-agent`。以前的前后端发布脚本会删除该 Deployment/Service；新脚本已取消此行为，
  但不会自动重建已经删除的服务。

若在同一 K3s 主机部署 SAM，请先确认磁盘与内存容量，然后在含项目源码的服务器目录执行：

```bash
docker build --build-arg INSTALL_SAM2=true --build-arg DOWNLOAD_SAM2_CHECKPOINT=true \
  -t weather_python_agent:sam2-cpu ./python-agent
docker save weather_python_agent:sam2-cpu | k3s ctr images import -
kubectl apply -f k8s/05-python-agent.yaml
kubectl -n weather set image deployment/python-agent python-agent=weather_python_agent:sam2-cpu
kubectl -n weather rollout status deployment/python-agent --timeout=300s
```

此镜像包含 tiny 权重和 CPU 推理依赖，构建较大，不纳入每次前后端发布。
Docker 构建 SAM 时复用已安装的 CPU PyTorch，禁用 pip 构建隔离，避免隔离环境重复下载另一份 PyTorch。
清单的 `/health` 探针只确认服务启动；还需通过已登录的 GIS 页面发送真实影像，验证权重加载和推理。

也可将同一 Python 推理接口部署在独立服务器，设置 GitHub 仓库 **Variables** 中的
`SAM2_SERVICE_URL`（例如内网的 `http://sam-server:8000`），前后端发布流程会将它写入后端环境变量。
该地址必须能从后端 Pod 访问；线上 Pod 的 `127.0.0.1` 指向 Pod 自身，不是你的电脑。
推理服务应通过私网向后端开放。

## Apply

Run these commands on the server from `/opt/weather`:

```bash
docker-compose down

curl -sfL https://get.k3s.io | sh -
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml

docker save weather_backend:latest weather_frontend:latest weather_python_agent:latest mysql:8.4.9 redis:7.4-alpine postgis/postgis:16-3.5 -o /tmp/weather-images.tar
k3s ctr images import /tmp/weather-images.tar

kubectl apply -f k8s/00-namespace.yaml
kubectl -n weather create configmap mysql-init --from-file=deploy/mysql/init --dry-run=client -o yaml | kubectl apply -f -
kubectl -n weather create configmap postgis-init --from-file=deploy/postgis/init --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f k8s/
kubectl -n weather get pods -w
```
