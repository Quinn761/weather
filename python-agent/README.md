# Python Agent

## SAM 2.1 点击圈地（可选）

GIS 页保留原来的「AI 圈地」（Roboflow），新增「SAM 2.1 圈地」。开启后点击田块内部，
模型以该点作为前景提示，选择包含点击点的最高评分掩膜及其连通区域，预览后点击「提交地块」。
输入目前为点击位置的单张天地图影像瓦片；无人机图层不作为输入。触及影像边缘时界面会提示
边界可能截断。单点分割不能保证田埂识别准确，请检查预览。当前输出外边界，掩膜内部孔洞填平。

调用链：浏览器 → `POST /api/gis/delineate/sam2`（Java，要求 `gis:write`）
→ Python `POST /gis/delineate/sam2` → SAM 2.1 → WGS84 GeoJSON → 原有保存接口。
Python 推理端口应仅对后端开放。SAM 模型延迟加载，并串行处理请求，避免不同图片的预测状态混用。
保存属性包含 `source: sam2.1`、`modelScore`、`touchesImageEdge`、面积及周长。
`modelScore` 为模型的掩膜质量预测分数，不是农田分类置信度。

### 本地安装

本项目 Windows 本地环境安装完成后，可直接运行 `python-agent/start-sam2.cmd`。
脚本使用根目录 `.venv-sam2`、`checkpoints/sam2.1_hiera_tiny.pt` 和 CPU 推理，监听
`127.0.0.1:8000`。若该端口已有本服务运行，无需重复启动。

已验证的本机依赖组合：Python 3.11、PyTorch 2.5.1+cpu、torchvision 0.20.1+cpu，
SAM 官方源码提交 `2b90b9f5ceec907a1c18123530e92e794ad901a4`。
CPU 安装 SAM 时设置 `SAM2_BUILD_CUDA=0`，跳过 CUDA 扩展构建。

使用独立 Python 环境，先按 [PyTorch 官方说明](https://pytorch.org/get-started/locally/) 安装匹配
CPU/CUDA 的 PyTorch 与 torchvision，再安装：

```powershell
python -m pip install -r requirements-sam2.txt
New-Item -ItemType Directory -Force checkpoints
Invoke-WebRequest 'https://dl.fbaipublicfiles.com/segment_anything_2/092824/sam2.1_hiera_tiny.pt' -OutFile checkpoints/sam2.1_hiera_tiny.pt
$env:SAM2_CHECKPOINT = (Resolve-Path checkpoints/sam2.1_hiera_tiny.pt).Path
$env:SAM2_CONFIG = 'configs/sam2.1/sam2.1_hiera_t.yaml'
$env:SAM2_DEVICE = 'cpu'
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

以上命令在 `python-agent` 目录执行；安装 Git 后才能安装 Git 依赖。
GPU 可用时可设置 `SAM2_DEVICE=cuda`，或 `auto` 自动选择。CPU 可用于试运行，但可能较慢。
配置与权重必须匹配；默认使用较小的 SAM 2.1 tiny。权重不提交到 Git。
Java 默认连接 `http://127.0.0.1:8000`，远程服务通过 Java 进程环境变量 `SAM2_SERVICE_URL` 设置。
Python 本地启动读取进程环境变量，不会自动读取根目录 `.env`。
官方推荐 Windows 用户使用 WSL；参见 [SAM 2 官方安装说明](https://github.com/facebookresearch/sam2)。

### Docker Compose

把 tiny 权重放在 `python-agent/checkpoints/sam2.1_hiera_tiny.pt`，在根目录 `.env` 设置：

```dotenv
INSTALL_SAM2=true
SAM2_CHECKPOINT=/models/sam2.1_hiera_tiny.pt
SAM2_CONFIG=configs/sam2.1/sam2.1_hiera_t.yaml
SAM2_DEVICE=cpu
```

运行 `docker compose up -d --build python-agent backend frontend`。
默认 Compose 为 CPU 配置；GPU 部署还需要主机 NVIDIA 容器运行时及容器 GPU 资源配置。
若使用独立 GPU 推理服务，只需将后端 `SAM2_SERVICE_URL` 指向运行同一 Python 接口的服务，
不必在默认 python-agent 镜像安装 SAM。未启用 SAM 时原有 Agent 和 Roboflow 功能仍可运行。

Kubernetes 的后端默认连接 `http://python-agent:8000`。启用集群内推理时，需要使用
`--build-arg INSTALL_SAM2=true` 构建 Python 镜像，将权重卷挂载到该容器，并设置同样的
`SAM2_CHECKPOINT`、`SAM2_CONFIG`、`SAM2_DEVICE` 环境变量；默认清单不包含模型权重。

### 验证

```powershell
python -m pip install httpx numpy "opencv-python-headless>=4.10,<5"
python -m unittest discover -s tests -v
```

测试覆盖 Web Mercator 坐标转换、点击连通区域筛选、边缘提示、参数校验和未配置/忙碌状态。
测试不下载权重；真实模型精度与耗时需在安装权重后使用实际影像验证。

这是 Weather Data Hub 的独立 Python AI Agent 服务，使用 FastAPI 开发。

## 本地启动

```bash
cd python-agent
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

健康检查：

```bash
curl http://127.0.0.1:8000/health
```

## 核心接口

- `GET /health`：检查 Python Agent 是否可用。
- `POST /agent/review`：供 Spring Boot Agent 调用，用 Python 生成最终回答。
- `POST /agent/run`：独立练习接口，方便你学习 FastAPI 请求和响应。

## Python 学习路线

1. 先学变量、函数、列表、字典、条件、循环。
2. 再学类、类型标注、异常处理、模块导入。
3. 然后学 FastAPI：`@app.get`、`@app.post`、Pydantic 请求模型。
4. 接着学调用大模型：环境变量、OpenAI 兼容接口、错误处理。
5. 最后学 Agent：规划、工具调用、RAG、记忆、trace。

建议先读 `app/main.py`，从 `health()`、`run_agent()`、`review()` 三个函数开始。
