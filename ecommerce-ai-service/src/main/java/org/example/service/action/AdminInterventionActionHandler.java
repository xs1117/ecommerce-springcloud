package org.example.service.action;

import org.springframework.stereotype.Component;

@Component
public class AdminInterventionActionHandler implements ActionHandler {

    private static final String ACTION_TYPE = "REQUEST_ADMIN_INTERVENTION";

    @Override
    public String actionType() {
        return ACTION_TYPE;
    }

    @Override
    public String remark() {
        return "AI客服发起平台介入";
    }

    @Override
    public String missingOrderReply() {
        return "我可以帮你申请平台客服介入处理。请先告诉我订单号（例如：订单号 202604150001）。";
    }

    @Override
    public String readyReply(String orderNo) {
        return "我已准备好为你申请平台客服介入（订单号：" + orderNo + "）。请点击确认，或回复“取消”。";
    }

    @Override
    public String executedReply() {
        return "已为你提交平台介入申请，客服会尽快跟进。";
    }

    @Override
    public boolean supports(String normalizedMessage) {
        return normalizedMessage.contains("介入")
                || normalizedMessage.contains("投诉")
                || normalizedMessage.contains("人工客服")
                || normalizedMessage.contains("平台客服");
    }

    @Override
    public int priority() {
        return 10;
    }
}

