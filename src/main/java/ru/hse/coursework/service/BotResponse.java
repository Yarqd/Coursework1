package ru.hse.coursework.service;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import ru.hse.coursework.model.MediaAsset;

public record BotResponse(String text, ReplyKeyboard keyboard, MediaAsset photo) {
    public static BotResponse of(String text) {
        return new BotResponse(text, null, null);
    }

    public static BotResponse of(String text, ReplyKeyboard keyboard) {
        return new BotResponse(text, keyboard, null);
    }

    public static BotResponse photo(MediaAsset photo, String caption, ReplyKeyboard keyboard) {
        return new BotResponse(caption, keyboard, photo);
    }

    public BotResponse withText(String newText) {
        return new BotResponse(newText, keyboard, photo);
    }
}
