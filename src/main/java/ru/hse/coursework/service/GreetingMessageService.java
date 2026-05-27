package ru.hse.coursework.service;

import org.springframework.stereotype.Service;

@Service
public class GreetingMessageService {
    public String replyTo(String incomingText, String firstName) {
        if (incomingText == null || incomingText.isBlank()) {
            return helpMessage();
        }

        String normalizedText = incomingText.trim();
        if ("/start".equalsIgnoreCase(normalizedText)) {
            return startMessage(firstName);
        }
        if ("/help".equalsIgnoreCase(normalizedText)) {
            return helpMessage();
        }
        return helpMessage();
    }

    public String unsupportedMessage() {
        return "Пока я понимаю только текстовые команды. Напиши /start, чтобы начать.";
    }

    private String startMessage(String firstName) {
        String name = firstName == null || firstName.isBlank() ? "друг" : firstName;
        return "Привет, " + name + "! Я бот для интервального повторения. MVP запущен: скоро здесь появятся карточки.";
    }

    private String helpMessage() {
        return "Я пока умею только приветствовать. Напиши /start.";
    }
}
