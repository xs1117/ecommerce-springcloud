package org.example.service.action;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ActionRegistry {

	private final List<ActionHandler> handlers;
	private final Map<String, ActionHandler> byActionType;

	public ActionRegistry(List<ActionHandler> handlers) {
		this.handlers = handlers.stream()
				.sorted(Comparator.comparingInt(ActionHandler::priority))
				.toList();
		this.byActionType = this.handlers.stream()
				.collect(Collectors.toMap(
						handler -> handler.actionType().toUpperCase(Locale.ROOT),
						Function.identity(),
						(left, ignored) -> left
				));
	}

	public Optional<ActionHandler> resolve(String message) {
		if (!StringUtils.hasText(message)) {
			return Optional.empty();
		}
		String normalized = message.toLowerCase(Locale.ROOT);
		return handlers.stream().filter(handler -> handler.supports(normalized)).findFirst();
	}

	public String executedReply(String actionType) {
		if (!StringUtils.hasText(actionType)) {
			return "已为你提交申请，平台会尽快处理。";
		}
		ActionHandler handler = byActionType.get(actionType.trim().toUpperCase(Locale.ROOT));
		return handler == null ? "已为你提交申请，平台会尽快处理。" : handler.executedReply();
	}
}

