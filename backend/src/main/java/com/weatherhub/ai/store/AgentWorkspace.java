package com.weatherhub.ai.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weatherhub.ai.dto.AgentMessageVO;
import com.weatherhub.ai.dto.AgentSessionDetailVO;
import com.weatherhub.ai.dto.AgentSessionVO;
import com.weatherhub.ai.dto.AgentTaskVO;
import com.weatherhub.ai.dto.TraceStep;
import com.weatherhub.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AgentWorkspace {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AiSessionMapper sessionMapper;
    private final AiMessageMapper messageMapper;
    private final AiMemoryMapper memoryMapper;

    public AgentWorkspace(AiSessionMapper sessionMapper, AiMessageMapper messageMapper, AiMemoryMapper memoryMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.memoryMapper = memoryMapper;
    }

    public List<AgentSessionVO> listSessions(Long userId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<AiSession>()
                        .eq(AiSession::getUserId, userId)
                        .orderByDesc(AiSession::getUpdatedAt)
                        .last("LIMIT 50"))
                .stream()
                .map(item -> new AgentSessionVO(item.getId(), item.getTitle(), item.getUpdatedAt()))
                .toList();
    }

    public AiSession createSession(Long userId, String title) {
        AiSession session = new AiSession();
        session.setUserId(userId);
        session.setTitle(trimTitle(title));
        LocalDateTime now = LocalDateTime.now();
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionMapper.insert(session);
        return session;
    }

    public AiSession requireSession(Long userId, Long sessionId) {
        AiSession session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BusinessException(404, "会话不存在");
        }
        return session;
    }

    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        requireSession(userId, sessionId);
        messageMapper.delete(new LambdaQueryWrapper<AiMessage>().eq(AiMessage::getSessionId, sessionId));
        sessionMapper.deleteById(sessionId);
    }

    public void retitleIfPlaceholder(AiSession session, String question) {
        if (session == null || !"新任务".equals(session.getTitle())) {
            return;
        }
        session.setTitle(trimTitle(question));
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    public AgentSessionDetailVO detail(Long userId, Long sessionId) {
        AiSession session = requireSession(userId, sessionId);
        List<AgentMessageVO> messages = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getSessionId, sessionId)
                        .orderByAsc(AiMessage::getId))
                .stream()
                .map(this::toMessage)
                .toList();
        return new AgentSessionDetailVO(
                new AgentSessionVO(session.getId(), session.getTitle(), session.getUpdatedAt()),
                messages,
                memories(userId)
        );
    }

    public void saveMessage(Long sessionId, String role, String content, String agent, String payload) {
        AiMessage message = new AiMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setAgent(agent);
        message.setPayload(payload);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        AiSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
    }

    public List<String> memories(Long userId) {
        return memoryMapper.selectList(new LambdaQueryWrapper<AiMemory>()
                        .eq(AiMemory::getUserId, userId)
                        .orderByDesc(AiMemory::getUpdatedAt)
                        .last("LIMIT 8"))
                .stream()
                .map(item -> item.getMemKey() + "：" + item.getMemValue())
                .toList();
    }

    public void remember(Long userId, String key, String value) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        String clipped = value.length() > 400 ? value.substring(0, 400) : value;
        memoryMapper.upsert(userId, key, clipped);
    }

    private AgentMessageVO toMessage(AiMessage message) {
        List<AgentTaskVO> plan = new ArrayList<>();
        List<String> usedTools = new ArrayList<>();
        List<String> ragSources = new ArrayList<>();
        List<TraceStep> trace = new ArrayList<>();
        String mode = "";
        if (StringUtils.hasText(message.getPayload())) {
            try {
                JsonNode root = JSON.readTree(message.getPayload());
                mode = text(root.get("mode"));
                JsonNode node = root.get("plan");
                if (node != null && node.isArray()) {
                    node.forEach(item -> plan.add(new AgentTaskVO(
                            text(item.get("agent")),
                            text(item.get("title")),
                            text(item.get("status")),
                            text(item.get("detail"))
                    )));
                }
                usedTools.addAll(strings(root.get("usedTools")));
                ragSources.addAll(strings(root.get("ragSources")));
                JsonNode traceNode = root.get("trace");
                if (traceNode != null && traceNode.isArray()) {
                    traceNode.forEach(item -> trace.add(new TraceStep(text(item.get("stage")), text(item.get("detail")))));
                }
            } catch (Exception ignored) {
                // 旧数据没有规划快照时忽略
            }
        }
        return new AgentMessageVO(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getAgent(),
                message.getCreatedAt(),
                mode,
                plan,
                usedTools,
                ragSources,
                trace
        );
    }

    private static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                String value = text(item);
                if (!value.isEmpty()) {
                    values.add(value);
                }
            });
        }
        return values;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asString();
    }

    static String trimTitle(String title) {
        String value = title == null ? "新任务" : title.trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) {
            return "新任务";
        }
        return value.length() > 24 ? value.substring(0, 24) + "…" : value;
    }
}
