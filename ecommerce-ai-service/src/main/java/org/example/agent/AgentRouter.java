package org.example.agent;

import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Comparator;
import java.util.List;

@Component
public class AgentRouter {

    private final List<CustomerAgent> agents;

    public AgentRouter(List<CustomerAgent> agents) {
        Assert.notEmpty(agents, "agents must not be empty");
        this.agents = agents.stream()
                .sorted(Comparator.comparingInt(CustomerAgent::priority).thenComparing(CustomerAgent::agentName))
                .toList();
    }

    public CustomerAgent route(ConversationContext context) {
        return agents.stream()
                .filter(agent -> agent.supports(context))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No available agent matched the current conversation"));
    }
}

