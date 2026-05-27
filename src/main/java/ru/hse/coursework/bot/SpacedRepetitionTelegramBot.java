package ru.hse.coursework.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.hse.coursework.config.TelegramBotProperties;
import ru.hse.coursework.service.GreetingMessageService;

@Component
@ConditionalOnProperty(prefix = "telegram.bot", name = "token")
public class SpacedRepetitionTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {
    private static final Logger log = LoggerFactory.getLogger(SpacedRepetitionTelegramBot.class);

    private final TelegramBotProperties properties;
    private final GreetingMessageService greetingMessageService;
    private final TelegramClient telegramClient;

    public SpacedRepetitionTelegramBot(TelegramBotProperties properties, GreetingMessageService greetingMessageService) {
        this.properties = properties;
        this.greetingMessageService = greetingMessageService;
        this.telegramClient = new OkHttpTelegramClient(properties.token());
    }

    @Override
    public String getBotToken() {
        return properties.token();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        if (update == null || !update.hasMessage()) {
            return;
        }

        Message message = update.getMessage();
        if (!message.hasText()) {
            sendMessage(message.getChatId(), greetingMessageService.unsupportedMessage());
            return;
        }

        String incomingText = message.getText();
        String firstName = message.getFrom() == null ? null : message.getFrom().getFirstName();
        String response = greetingMessageService.replyTo(incomingText, firstName);
        log.info("Received message from chatId={}, text={}", message.getChatId(), incomingText);
        sendMessage(message.getChatId(), response);
    }

    @AfterBotRegistration
    public void onBotRegistered(BotSession botSession) {
        log.info("Telegram bot registered. sessionRunning={}", botSession.isRunning());
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException exception) {
            log.error("Failed to send Telegram message to chatId={}", chatId, exception);
        }
    }
}
