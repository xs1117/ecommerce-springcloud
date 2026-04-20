package org.example.rag;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record KnowledgeChunk(
		String source,
		String title,
		int index,
		String content,
		boolean requiresConfirmation,
		String contentHash
) {
	public String id() {
		// Qdrant point id must be an unsigned integer or UUID.
		String stableKey = source + "|" + index + "|" + contentHash;
		return UUID.nameUUIDFromBytes(stableKey.getBytes(StandardCharsets.UTF_8)).toString();
	}

	public String displayName() {
		return (title == null || title.isBlank() ? "知识片段" : title) + "-" + (index + 1);
	}
}

