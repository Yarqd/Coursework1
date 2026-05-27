package ru.hse.coursework.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GreetingMessageServiceTest {
    private final GreetingMessageService service = new GreetingMessageService();

    @Test
    void repliesWithPersonalGreetingOnStartCommand() {
        String response = service.replyTo("/start", "Ярослав");

        assertThat(response)
                .contains("Привет, Ярослав")
                .contains("интервального повторения")
                .contains("MVP запущен");
    }

    @Test
    void repliesWithHelpForUnknownText() {
        String response = service.replyTo("что ты умеешь?", "Ярослав");

        assertThat(response).isEqualTo("Я пока умею только приветствовать. Напиши /start.");
    }

    @Test
    void repliesWithUnsupportedMessageForNonTextUpdates() {
        String response = service.unsupportedMessage();

        assertThat(response).contains("/start");
    }
}
