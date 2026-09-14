package com.weatherhub.ai.agent;

import com.weatherhub.ai.llm.*;
import com.weatherhub.ai.tool.McpToolCatalog;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ContextualPlannerTest {
    @Test
    void passesHistoryAndKeepsResolvedToolArguments() {
        var llm = mock(LlmClient.class);
        var catalog = mock(McpToolCatalog.class);
        when(llm.configured()).thenReturn(true);
        when(catalog.names()).thenReturn(List.of("get_system_data"));
        when(catalog.openAiTools()).thenReturn(List.of());
        when(llm.chat(anyList(), anyList())).thenReturn(LlmMessage.assistantTools(List.of(
                new LlmToolCall("1", "get_system_data", "{\"scope\":\"users\",\"keyword\":\"alice\"}"))));
        var history = List.of(LlmMessage.user("查询alice账号"), LlmMessage.assistant("已查到alice"));
        var result = new ContextualPlanner(llm, catalog).plan("她有什么权限？", history);
        assertEquals("get_system_data", result.get(1).tool());
        assertTrue(result.get(1).arguments().contains("alice"));
        verify(llm).chat(argThat(messages -> messages.containsAll(history)
                && messages.getLast().content().equals("她有什么权限？")), anyList());
    }

    @Test
    void chatDoesNotExecuteUnregisteredToolsAndFailureFallsBack() {
        var llm = mock(LlmClient.class);
        var catalog = mock(McpToolCatalog.class);
        when(llm.configured()).thenReturn(true);
        when(catalog.names()).thenReturn(List.of());
        when(catalog.openAiTools()).thenReturn(List.of());
        when(llm.chat(anyList(), anyList())).thenReturn(LlmMessage.assistantTools(List.of(
                new LlmToolCall("1", "execute_sql", "{}"))));
        var planner = new ContextualPlanner(llm, catalog);
        assertTrue(planner.plan("你好", List.of()).stream().allMatch(step -> step.tool() == null));
        when(llm.chat(anyList(), anyList())).thenThrow(new RuntimeException("offline"));
        assertTrue(planner.plan("无锡天气", List.of()).stream().anyMatch(step -> "get_current_weather".equals(step.tool())));
    }
}
