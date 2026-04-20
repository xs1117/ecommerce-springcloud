package org.example.service.action;

public interface ActionHandler {

    String actionType();

    String remark();

    String missingOrderReply();

    String readyReply(String orderNo);

    String executedReply();

    boolean supports(String normalizedMessage);

    default int priority() {
        return 100;
    }
}

