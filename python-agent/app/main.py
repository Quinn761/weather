import json
import os
from typing import Any, Iterator

from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from openai import OpenAI
from pydantic import BaseModel, Field
from app.sam2_service import router as sam2_router


class TraceStep(BaseModel):
    stage: str
    detail: str


class ReviewRequest(BaseModel):
    user_id: int
    session_id: int | None = None
    message: str = Field(min_length=1)
    evidences: list[str] = Field(default_factory=list)
    used_tools: list[str] = Field(default_factory=list)
    rag_sources: list[str] = Field(default_factory=list)
    trace: list[TraceStep] = Field(default_factory=list)


class ReviewResponse(BaseModel):
    reply: str
    mode: str
    trace: list[TraceStep] = Field(default_factory=list)


class AgentRunRequest(BaseModel):
    user_id: int = 0
    session_id: int | None = None
    message: str = Field(min_length=1)
    permissions: list[str] = Field(default_factory=list)


class AgentRunResponse(BaseModel):
    reply: str
    mode: str
    used_tools: list[str] = Field(default_factory=list)
    rag_sources: list[str] = Field(default_factory=list)
    trace: list[TraceStep] = Field(default_factory=list)






app = FastAPI(title="Weather Data Hub Python Agent", version="0.1.0")
app.include_router(sam2_router)


def ai_configured() -> bool:
    return bool(os.getenv("AI_API_KEY", "").strip())




def openai_client() -> OpenAI:
    base_url = os.getenv("AI_BASE_URL", "https://api.deepseek.com").strip().rstrip("/")
    if "/v1" not in base_url:
        base_url = f"{base_url}/v1"
    return OpenAI(api_key=os.getenv("AI_API_KEY", "").strip(), base_url=base_url)








def local_review(req: ReviewRequest) -> str:
    evidence = "\n\n".join(req.evidences).strip() or "没有额外证据。"
    tools = "、".join(req.used_tools) if req.used_tools else "未调用工具"
    sources = "、".join(req.rag_sources) if req.rag_sources else "未命中 RAG"
    return (
        f"我已根据系统证据回答你的问题：{req.message}\n\n"
        f"{evidence}\n\n"
        f"工具：{tools}\n"
        f"知识来源：{sources}"
    )


def llm_review(req: ReviewRequest) -> str:
    evidence = "\n\n".join(req.evidences).strip() or "没有额外证据。"
    client = openai_client()
    model = os.getenv("AI_MODEL", "deepseek-chat")
    completion = client.chat.completions.create(
        model=model,
        messages=[
            {
                "role": "system",
                "content": (
                    "你是 Weather Data Hub 的 Python Agent reviewer。"
                    "只能根据证据回答，不要编造系统数据。"
                    "回答使用简体中文，先给结论，再补充关键依据。"
                ),
            },
            {
                "role": "user",
                "content": f"用户问题：{req.message}\n\n证据：\n{evidence}",
            },
        ],
        temperature=0.2,
    )
    return completion.choices[0].message.content or ""


def llm_review_stream(req: ReviewRequest) -> Iterator[str]:
    evidence = "\n\n".join(req.evidences).strip() or "没有额外证据。"
    client = openai_client()
    model = os.getenv("AI_MODEL", "deepseek-chat")
    stream = client.chat.completions.create(
        model=model,
        messages=[
            {
                "role": "system",
                "content": (
                    "你是 Weather Data Hub 的 Python Agent reviewer。"
                    "只能根据证据回答，不要编造系统数据。"
                    "回答使用简体中文，先给结论，再补充关键依据。"
                ),
            },
            {
                "role": "user",
                "content": f"用户问题：{req.message}\n\n证据：\n{evidence}",
            },
        ],
        temperature=0.2,
        stream=True,
    )
    for chunk in stream:
        if not chunk.choices:
            continue
        delta = chunk.choices[0].delta.content
        if delta:
            yield delta


def sse(payload: dict[str, Any]) -> bytes:
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n".encode("utf-8")


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "UP",
        "name": "weather-python-agent",
        "llmConfigured": ai_configured(),
    }


@app.post("/agent/review", response_model=ReviewResponse)
def review(req: ReviewRequest) -> ReviewResponse:
    trace = list(req.trace)
    trace.append(TraceStep(stage="python-agent", detail="Python reviewer received evidence"))
    if ai_configured():
        try:
            reply = llm_review(req).strip()
            if reply:
                trace.append(TraceStep(stage="python-agent", detail="LLM reviewer completed"))
                return ReviewResponse(reply=reply, mode="python-llm", trace=trace)
        except Exception as exc:
            trace.append(TraceStep(stage="python-agent", detail=f"LLM failed: {exc}"))
    trace.append(TraceStep(stage="python-agent", detail="Local reviewer completed"))
    return ReviewResponse(reply=local_review(req), mode="python-local", trace=trace)


@app.post("/agent/review/stream")
def review_stream(req: ReviewRequest) -> StreamingResponse:
    trace = list(req.trace)
    trace.append(TraceStep(stage="python-agent", detail="Python reviewer stream started"))

    def events() -> Iterator[bytes]:
        if ai_configured():
            try:
                full: list[str] = []
                for delta in llm_review_stream(req):
                    full.append(delta)
                    yield sse({"text": delta})
                reply = "".join(full).strip()
                if reply:
                    yield sse({"done": True, "mode": "python-llm", "reply": reply})
                    return
            except Exception as exc:
                yield sse({"error": f"LLM failed: {exc}"})
        reply = local_review(req)
        yield sse({"text": reply})
        yield sse({"done": True, "mode": "python-local", "reply": reply})

    return StreamingResponse(
        events(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@app.post("/agent/run", response_model=AgentRunResponse)
def run_agent(req: AgentRunRequest) -> AgentRunResponse:
    trace = [
        TraceStep(stage="planner", detail="Python Agent received task"),
        TraceStep(stage="reviewer", detail="Standalone demo response generated"),
    ]
    return AgentRunResponse(
        reply=f"Python Agent 已收到任务：{req.message}",
        mode="python-local",
        trace=trace,
    )
