package org.example.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MiniMaxChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(MiniMaxChatModel.class);
    private static final Pattern THINK_BLOCK_PATTERN = Pattern.compile("<think>(.*?)</think>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public MiniMaxChatModel(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        int timeout = Math.max(aiProperties.getChatTimeoutMillis(), 1000);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.min(timeout, 5000));
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public @NonNull ChatResponse call(@NonNull Prompt prompt) {
        if (!aiProperties.isLlmEnabled()) {
            throw new IllegalStateException("LLM is disabled by ai.llm-enabled");
        }
        if (!StringUtils.hasText(aiProperties.getApiKey())) {
            throw new IllegalStateException("MiniMax API key is missing. Set ai.api-key or MINIMAX_API_KEY.");
        }

        List<Map<String, Object>> messages = toMiniMaxMessages(prompt);
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Prompt does not contain any chat messages.");
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", aiProperties.getModel());
        requestBody.put("messages", messages);
        requestBody.put("stream", false);

        String responseBody = invokeWithRetry(requestBody);

        String content = formatThinkAndAnswer(extractAssistantContent(responseBody));
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("MiniMax returned an empty response body.");
        }

        AssistantMessage assistantMessage = new AssistantMessage(content.trim());
        return new ChatResponse(List.of(new Generation(assistantMessage)));
    }

    private String invokeWithRetry(Map<String, Object> requestBody) {
        int maxAttempts = Math.max(aiProperties.getMaxRetryAttempts(), 1);
        URI targetUri = buildUri();
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return restClient.post()
                        .uri(targetUri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(aiProperties.getApiKey()))
                        .body(requestBody)
                        .retrieve()
                        .toEntity(String.class)
                        .getBody();
            } catch (Exception ex) {
                lastException = ex;
                if (!isRetryable(ex) || attempt >= maxAttempts) {
                    break;
                }
                long sleepMillis = computeBackoffMillis(attempt);
                log.warn("LLM relay call failed, retrying. attempt={}/{}, backoff={}ms, error={}",
                        attempt,
                        maxAttempts,
                        sleepMillis,
                        ex.getMessage());
                sleepQuietly(sleepMillis);
            }
        }
        throw toFinalException(lastException, maxAttempts, targetUri);
    }

    private IllegalStateException toFinalException(Exception lastException, int maxAttempts, URI targetUri) {
        if (lastException instanceof IllegalStateException stateException && StringUtils.hasText(stateException.getMessage())) {
            return stateException;
        }
        if (lastException instanceof RestClientResponseException restException) {
            int status = restException.getStatusCode().value();
            String body = restException.getResponseBodyAsString();
            String message = StringUtils.hasText(body)
                    ? "中转服务返回异常（HTTP " + status + "）：" + truncate(body)
                    : "中转服务返回异常（HTTP " + status + "）";
            return new IllegalStateException(message, lastException);
        }
        log.warn("LLM relay call failed after retries. attempts={}, uri={}, error={}",
                maxAttempts,
                targetUri,
                lastException == null ? "unknown" : lastException.getMessage());
        return new IllegalStateException("MiniMax 调用失败：请求超时或中转服务暂时不可用，请稍后重试", lastException);
    }

    private boolean isRetryable(Exception ex) {
        if (ex instanceof RestClientResponseException restEx) {
            int status = restEx.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        if (ex instanceof ResourceAccessException) {
            return true;
        }
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof SocketTimeoutException || cause instanceof ConnectException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private long computeBackoffMillis(int attempt) {
        long base = Math.max(aiProperties.getRetryBackoffMillis(), 100);
        long delay = (long) (base * Math.pow(2, Math.max(0, attempt - 1)));
        return Math.min(delay, 3000L);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private URI buildUri() {
        String baseUrl = normalizeBaseUrl(aiProperties.getBaseUrl());
        String path = normalizePath(aiProperties.getChatPath());
        StringBuilder uri = new StringBuilder(baseUrl).append(path);
        // GroupId is only needed for native MiniMax endpoint style.
        if (StringUtils.hasText(aiProperties.getGroupId()) && path.contains("chatcompletion_v2")) {
            uri.append(uri.indexOf("?") >= 0 ? "&" : "?")
                    .append("GroupId=")
                    .append(URLEncoder.encode(aiProperties.getGroupId().trim(), StandardCharsets.UTF_8));
        }
        return URI.create(uri.toString());
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "https://api.whatai.cc";
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/v1/chat/completions";
        }
        String trimmed = path.trim();
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        return trimmed;
    }

    private String buildAuthorizationHeader(String apiKey) {
        String trimmed = apiKey == null ? "" : apiKey.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            trimmed = trimmed.substring(7).trim();
        }
        return "Bearer " + trimmed;
    }

    private List<Map<String, Object>> toMiniMaxMessages(Prompt prompt) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Message message : prompt.getInstructions()) {
            if (message == null || !StringUtils.hasText(message.getText())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", toRole(message.getMessageType()));
            item.put("content", message.getText());
            result.add(item);
        }
        return result;
    }

    private String toRole(MessageType messageType) {
        if (messageType == null) {
            return MessageType.USER.name().toLowerCase(Locale.ROOT);
        }
        return switch (messageType) {
            case SYSTEM -> "system";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
            default -> "user";
        };
    }

    private String extractAssistantContent(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String miniMaxError = extractMiniMaxError(root);
            if (StringUtils.hasText(miniMaxError)) {
                throw new IllegalStateException(miniMaxError);
            }
            for (String path : List.of(
                    "choices.0.message.content",
                    "choices.0.message.reasoning_content",
                    "choices.0.content",
                    "data.choices.0.message.content",
                    "data.choices.0.content",
                    "reply",
                    "output_text",
                    "data.reply",
                    "data.output_text",
                    "content",
                    "data.content"
            )) {
                String value = readText(root, path);
                if (StringUtils.hasText(value)) {
                    if ("choices.0.message.reasoning_content".equals(path)) {
                        String normalAnswer = readText(root, "choices.0.message.content");
                        if (StringUtils.hasText(normalAnswer)) {
                            return "【思考内容】\n" + value.trim() + "\n\n【正式回答】\n" + normalAnswer.trim();
                        }
                    }
                    return value;
                }
            }
            if (root.isTextual()) {
                return root.asText();
            }
            return root.toString();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("MiniMax 响应解析失败，请检查接口返回格式", ex);
        }
    }

    private String extractMiniMaxError(JsonNode root) {
        JsonNode baseResp = root.path("base_resp");
        if (!baseResp.isMissingNode() && !baseResp.isNull()) {
            int statusCode = baseResp.path("status_code").asInt(0);
            String statusMsg = baseResp.path("status_msg").asText("");
            if (statusCode != 0) {
                return "MiniMax 调用失败（status_code=" + statusCode + "）："
                        + (StringUtils.hasText(statusMsg) ? statusMsg : "请检查 API Secret Key、GroupId、模型名称和接口地址。");
            }
        }
        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            String message = error.path("message").asText("");
            if (!StringUtils.hasText(message)) {
                message = error.toString();
            }
            return "MiniMax 调用失败：" + message;
        }
        return "";
    }

    private String readText(JsonNode root, String path) {
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return "";
            }
            if (segment.matches("\\d+")) {
                int index = Integer.parseInt(segment);
                if (!current.isArray() || current.size() <= index) {
                    return "";
                }
                current = current.get(index);
            } else {
                current = current.get(segment);
            }
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            return "";
        }
        if (current.isTextual()) {
            return current.asText();
        }
        if (current.isArray()) {
            StringBuilder merged = new StringBuilder();
            for (JsonNode item : current) {
                if (item == null || item.isNull()) {
                    continue;
                }
                if (item.isTextual()) {
                    if (!merged.isEmpty()) {
                        merged.append('\n');
                    }
                    merged.append(item.asText());
                    continue;
                }
                String type = item.path("type").asText("");
                if ("text".equalsIgnoreCase(type)) {
                    String text = item.path("text").asText("");
                    if (StringUtils.hasText(text)) {
                        if (!merged.isEmpty()) {
                            merged.append('\n');
                        }
                        merged.append(text);
                    }
                }
            }
            if (!merged.isEmpty()) {
                return merged.toString();
            }
        }
        return current.toString();
    }

    private String formatThinkAndAnswer(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        Matcher matcher = THINK_BLOCK_PATTERN.matcher(content);
        if (!matcher.find()) {
            return content.trim();
        }

        List<String> thinkBlocks = new ArrayList<>();
        Set<String> deduplicate = new HashSet<>();
        matcher.reset();
        while (matcher.find()) {
            String block = matcher.group(1) == null ? "" : matcher.group(1).trim();
            if (StringUtils.hasText(block) && deduplicate.add(block)) {
                thinkBlocks.add(block);
            }
        }
        String think = String.join("\n\n", thinkBlocks);
        String answer = matcher.replaceAll("").trim();

        if (StringUtils.hasText(think) && StringUtils.hasText(answer)) {
            return "【思考内容】\n" + think + "\n\n【正式回答】\n" + answer;
        }
        if (StringUtils.hasText(answer)) {
            return answer;
        }
        return think;
    }

    private String truncate(String text) {
        int maxLength = 300;
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String cleaned = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (cleaned.length() <= maxLength) {
            return cleaned;
        }
        return cleaned.substring(0, maxLength) + "...";
    }
}


