package org.example.service.action;

import org.springframework.stereotype.Component;

@Component
public class ExchangeActionHandler implements ActionHandler {

    private static final String ACTION_TYPE = "APPLY_EXCHANGE";

    @Override
    public String actionType() {
        return ACTION_TYPE;
    }

    @Override
    public String remark() {
        return "AI客服发起换货";
    }

    @Override
    public String missingOrderReply() {
        return "我可以帮你发起换货申请。请先告诉我订单号（例如：订单号 202604150001）。";
    }

    @Override
    public String readyReply(String orderNo) {
        return "我已准备好为你发起换货申请（订单号：" + orderNo + "）。请点击确认，或回复“取消”。";
    }

    @Override
    public String executedReply() {
        return "已为你发起换货申请，商家会尽快处理。";
    }

    @Override
    public boolean supports(String normalizedMessage) {
        return normalizedMessage.contains("换货") || normalizedMessage.contains("exchange");
    }

    @Override
    public int priority() {
        return 20;
    }
}

