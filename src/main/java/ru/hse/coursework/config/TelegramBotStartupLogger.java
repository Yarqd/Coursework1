package ru.hse.coursework.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class TelegramBotStartupLogger implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TelegramBotStartupLogger.class);

    private final TelegramBotProperties properties;

    public TelegramBotStartupLogger(TelegramBotProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.configured()) {
            log.info("Telegram bot configuration found. username={}", blankToPlaceholder(properties.username()));
            return;
        }
        log.warn("Telegram bot token is not configured. Set TELEGRAM_BOT_TOKEN to start long polling.");
    }

    private String blankToPlaceholder(String value) {
        return value == null || value.isBlank() ? "<not set>" : value;
    }
}
