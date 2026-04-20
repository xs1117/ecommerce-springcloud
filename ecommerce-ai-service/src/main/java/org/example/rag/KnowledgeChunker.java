package org.example.rag;

import org.example.config.AiProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class KnowledgeChunker {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern TITLE_PATTERN = Pattern.compile("^(.+?)[：:]$");
    private static final Pattern REQUIRE_CONFIRMATION_PATTERN = Pattern.compile("退货|退款|换货|二次确认|apply_return", Pattern.CASE_INSENSITIVE);

    private final AiProperties aiProperties;

    public KnowledgeChunker(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public List<KnowledgeChunk> chunk(KnowledgeDocument document) {
        Objects.requireNonNull(document, "document");
        String source = safeSource(document.source());
        String content = Objects.toString(document.content(), "").replace("\r\n", "\n").trim();
        List<KnowledgeChunk> chunks = new ArrayList<>();
        if (content.isBlank()) {
            return chunks;
        }

        String currentTitle = inferDocumentTitle(source);
        List<String> body = new ArrayList<>();
        int index = 0;

        for (String rawLine : content.split("\\n")) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }

            String heading = resolveHeading(line);
            if (heading != null) {
                index = flushChunk(chunks, source, currentTitle, body, index);
                currentTitle = heading;
                body.clear();
                continue;
            }

            body.add(line);
        }

        flushChunk(chunks, source, currentTitle, body, index);
        if (chunks.isEmpty()) {
            chunks.add(buildChunk(source, currentTitle, 0, content));
        }
        return chunks;
    }

    private int flushChunk(List<KnowledgeChunk> chunks, String source, String title, List<String> body, int index) {
        if (body.isEmpty()) {
            return index;
        }
        String chunkText = String.join("\n", body).trim();
        if (chunkText.isBlank()) {
            body.clear();
            return index;
        }

        int maxChars = Math.max(120, aiProperties.getRag().getChunkMaxChars());
        if (chunkText.length() <= maxChars) {
            chunks.add(buildChunk(source, title, index, chunkText));
            body.clear();
            return index + 1;
        }

        List<String> sections = splitByLength(body, maxChars);
        for (String section : sections) {
            if (!section.isBlank()) {
                chunks.add(buildChunk(source, title, index++, section));
            }
        }
        body.clear();
        return index;
    }

    private List<String> splitByLength(List<String> lines, int maxChars) {
        List<String> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int overlapChars = Math.max(0, aiProperties.getRag().getChunkOverlapChars());
        for (String line : lines) {
            if (current.isEmpty()) {
                current.append(line);
                continue;
            }
            if (current.length() + 1 + line.length() > maxChars) {
                String section = current.toString().trim();
                if (!section.isBlank()) {
                    sections.add(section);
                }
                String carry = overlapChars <= 0 ? "" : tail(section, overlapChars);
                current.setLength(0);
                if (!carry.isBlank()) {
                    current.append(carry).append('\n');
                }
                current.append(line);
            } else {
                current.append('\n').append(line);
            }
        }
        if (!current.isEmpty()) {
            sections.add(current.toString().trim());
        }
        return sections;
    }

    private String tail(String value, int length) {
        if (value == null || value.isBlank() || length <= 0) {
            return "";
        }
        return value.length() <= length ? value : value.substring(Math.max(0, value.length() - length));
    }

    private KnowledgeChunk buildChunk(String source, String title, int index, String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        String hash = hash(source + "|" + title + "|" + index + "|" + normalized);
        boolean requiresConfirmation = REQUIRE_CONFIRMATION_PATTERN.matcher(normalized).find();
        String body = title == null || title.isBlank() ? normalized : title.trim() + "\n" + content.trim();
        return new KnowledgeChunk(source, title, index, body, requiresConfirmation, hash);
    }

    private String resolveHeading(String line) {
        var markdownHeading = HEADING_PATTERN.matcher(line);
        if (markdownHeading.matches()) {
            return markdownHeading.group(2).trim();
        }
        var titleHeading = TITLE_PATTERN.matcher(line);
        if (titleHeading.matches() && !line.startsWith("-") && !line.startsWith("*")) {
            String text = titleHeading.group(1).trim();
            if (!text.isBlank() && text.length() <= 48) {
                return text;
            }
        }
        return null;
    }

    private String inferDocumentTitle(String source) {
        String name = source;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < name.length()) {
            name = name.substring(slash + 1);
        }
        if (name.toLowerCase(Locale.ROOT).endsWith(".md")) {
            name = name.substring(0, name.length() - 3);
        }
        return name;
    }

    private String safeSource(String source) {
        return source == null || source.isBlank() ? "unknown" : source.trim();
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            return Integer.toHexString(value.hashCode());
        }
    }
}


