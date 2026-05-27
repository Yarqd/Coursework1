package ru.hse.coursework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram.bot")
public record TelegramBotProperties(String token, String username) {
    public boolean configured() {
        return token != null && !token.isBlank();
    }
}
