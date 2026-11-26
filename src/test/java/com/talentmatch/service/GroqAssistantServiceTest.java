package com.talentmatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroqAssistantServiceTest {
    private final GroqAssistantService service = new GroqAssistantService(new ObjectMapper(), "", "openai/gpt-oss-20b");
    @Test void readsPublicAnswerOnly() throws Exception {
        var result = service.parseResponse("{\"choices\":[{\"message\":{\"content\":\"Xin chào\",\"reasoning\":\"hidden\"},\"finish_reason\":\"stop\"}]}");
        assertEquals("Xin chào", result.text());
        assertNull(result.error());
    }
    @Test void detectsEmptyTokenLimitedResponse() throws Exception {
        var result = service.parseResponse("{\"choices\":[{\"message\":{\"content\":null,\"reasoning\":\"hidden\"},\"finish_reason\":\"length\"}]}");
        assertNull(result.text());
        assertTrue(result.error().contains("token"));
    }
    @Test void detectsMissingChoices() throws Exception {
        var result = service.parseResponse("{}");
        assertNull(result.text());
        assertNotNull(result.error());
    }
    @Test void marksTruncatedAnswer() throws Exception {
        var result = service.parseResponse("{\"choices\":[{\"message\":{\"content\":\"Xin chào\"},\"finish_reason\":\"length\"}]}");
        assertTrue(result.text().contains("giới hạn"));
    }
}
