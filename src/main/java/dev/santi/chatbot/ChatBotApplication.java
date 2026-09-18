package dev.santi.chatbot;

import dev.santi.chatbot.config.EnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ChatBotApplication {

    public static void main(String[] args) {
        EnvLoader.load();
        SpringApplication.run(ChatBotApplication.class, args);
    }

}
