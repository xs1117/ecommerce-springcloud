package org.example.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiBeansConfig {

    @Bean
    @Primary
    public ChatModel chatModel(AiProperties aiProperties, ObjectMapper objectMapper) {
        return new MiniMaxChatModel(aiProperties, objectMapper);
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}

