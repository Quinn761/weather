# Python Agent 学习与开发指南

这份文档按当前项目的 `python-agent` 服务来学 Python，不从抽象语法开始。

## 1. 你先看哪个文件

入口文件是：

```text
python-agent/app/main.py
```

它里面有三个最重要的接口：

```python
@app.get("/health")
def health():
    ...

@app.post("/agent/review")
def review(req: ReviewRequest):
    ...

@app.post("/agent/run")
def run_agent(req: AgentRunRequest):
    ...
```

`@app.get` 和 `@app.post` 是 FastAPI 的路由装饰器，表示这个函数会处理 HTTP 请求。

## 2. Python 基础从这里学

### 变量

```python
name = "Weather Data Hub"
count = 3
enabled = True
```

Python 不需要写 `String name`、`int count`，解释器会根据值推断类型。

### 列表

```python
tools = ["search_knowledge", "get_current_weather"]
tools.append("list_gis_features")
```

对应 Java 里的 `List<String>`。

### 字典

```python
data = {
    "status": "UP",
    "name": "weather-python-agent",
}
```

对应 Java 里的 `Map<String, Object>`。

### 函数

```python
def local_review(req: ReviewRequest) -> str:
    return "回答内容"
```

`-> str` 是类型标注，表示函数返回字符串。

## 3. Pydantic 请求模型

项目里这个类定义了 `/agent/review` 的请求体：

```python
class ReviewRequest(BaseModel):
    user_id: int
    session_id: int | None = None
    message: str = Field(min_length=1)
    evidences: list[str] = Field(default_factory=list)
```

含义：

- `user_id: int`：必须传整数。
- `session_id: int | None = None`：可以是整数，也可以为空。
- `message: str = Field(min_length=1)`：字符串不能为空。
- `list[str]`：字符串列表。

FastAPI 会自动把 JSON 请求体转成这个对象，也会自动校验参数。

## 4. 本地启动 Python Agent

```bash
cd python-agent
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

访问健康检查：

```bash
curl http://127.0.0.1:8000/health
```

测试独立 Agent：

```bash
curl -X POST http://127.0.0.1:8000/agent/run ^
  -H "Content-Type: application/json" ^
  -d "{\"user_id\":1,\"message\":\"帮我查无锡天气\"}"
```

## 5. Spring Boot 怎么调用它

后端新增了配置：

```yaml
weatherhub:
  ai:
    python-agent-url: ${PYTHON_AGENT_URL:}
```

如果 `PYTHON_AGENT_URL` 为空，项目继续走 Java 内置 Agent。

如果配置：

```bash
PYTHON_AGENT_URL=http://127.0.0.1:8000
```

Java Agent 在最终汇总回答时会调用 Python 的：

```text
POST /agent/review
```

Docker Compose 中已经配置为：

```text
PYTHON_AGENT_URL=http://python-agent:8000
```

## 6. 怎么接入大模型

设置环境变量：

```bash
set AI_API_KEY=你的key
set AI_BASE_URL=https://api.deepseek.com
set AI_MODEL=deepseek-chat
```

Python 里使用 OpenAI 兼容 SDK：

```python
client = OpenAI(api_key=..., base_url=...)
completion = client.chat.completions.create(...)
```

没有 `AI_API_KEY` 时，Python Agent 会走 `local_review()`，不会报错中断。

## 7. 下一步你可以练什么

1. 在 `run_agent()` 里根据关键词判断是否查询天气。
2. 新增一个 Python 工具函数，比如 `get_current_weather()`。
3. 把工具调用结果加入 `evidences`。
4. 给 `/agent/run` 返回 `used_tools` 和 `trace`。
5. 学会把异常用 `try/except` 包起来。

你真正要掌握的是这条链路：

```text
请求模型 -> 规划 -> 工具调用 -> 证据汇总 -> 大模型或本地回答 -> 响应模型
```
