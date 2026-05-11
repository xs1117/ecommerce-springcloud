package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReturnReasonSummarizerTest {

    @Test
    void shouldExtractReasonSummaryFromJsonPrefixPayload() throws Exception {
        ReturnReasonSummarizer summarizer = new ReturnReasonSummarizer(null, new AiProperties(), new ObjectMapper());

        String result = invokeExtract(summarizer, "json {\"reasonSummary\":\"商品无法正常开机使用，怀疑存在质量问题\"}");

        assertEquals("商品无法正常开机使用，怀疑存在质量问题", result);
    }

    @Test
    void shouldExtractReasonSummaryFromCodeFencePayload() throws Exception {
        ReturnReasonSummarizer summarizer = new ReturnReasonSummarizer(null, new AiProperties(), new ObjectMapper());

        String result = invokeExtract(summarizer, "```json\n{\"reasonSummary\":\"包装破损，担心内部商品受损\"}\n```");

        assertEquals("包装破损，担心内部商品受损", result);
    }

    @Test
    void shouldExtractReasonSummaryFromPlainJsonObject() throws Exception {
        ReturnReasonSummarizer summarizer = new ReturnReasonSummarizer(null, new AiProperties(), new ObjectMapper());

        String result = invokeExtract(summarizer, "{\"reasonSummary\":\"尺码偏小，不适合穿着\"}");

        assertEquals("尺码偏小，不适合穿着", result);
    }

    private String invokeExtract(ReturnReasonSummarizer summarizer, String input) throws Exception {
        Method method = ReturnReasonSummarizer.class.getDeclaredMethod("extractReasonSummary", String.class);
        method.setAccessible(true);
        return (String) method.invoke(summarizer, input);
    }
}

