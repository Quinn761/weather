package com.weatherhub.ai.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmClientTest {

    @Test
    void extractsStreamDelta() {
        String json = "{\"choices\":[{\"delta\":{\"content\":\"上\"}}]}";
        assertEquals("上", LlmClient.extractStreamDelta(json));
    }

    @Test
    void emptyWhenNoDelta() {
        assertEquals("", LlmClient.extractStreamDelta("{\"choices\":[]}"));
    }
}
