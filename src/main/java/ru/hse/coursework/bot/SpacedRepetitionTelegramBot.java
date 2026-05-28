package ru.hse.coursework.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.hse.coursework.model.MediaAsset;
import ru.hse.coursework.config.TelegramBotProperties;
import ru.hse.coursework.service.BotDialogService;
import ru.hse.coursework.service.BotResponse;
import ru.hse.coursework.service.MediaAssetService;
import ru.hse.coursework.service.UserService;
import ru.hse.coursework.storage.ObjectStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.Optional;

@Component
@ConditionalOnExpression("'${telegram.bot.token:}' != ''")
public class SpacedRepetitionTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {
    private static final Logger log = LoggerFactory.getLogger(SpacedRepetitionTelegramBot.class);

    private final TelegramBotProperties properties;
    private final BotDialogService botDialogService;
    private final MediaAssetService mediaAssetService;
    private final ObjectStorageService objectStorageService;
    private final UserService userService;
    private final TelegramClient telegramClient;

    public SpacedRepetitionTelegramBot(
            TelegramBotProperties properties,
            BotDialogService botDialogService,
            MediaAssetService mediaAssetService,
            ObjectStorageService objectStorageService,
            UserService userService
    ) {
        this.properties = properties;
        this.botDialogService = botDialogService;
        this.mediaAssetService = mediaAssetService;
        this.objectStorageService = objectStorageService;
        this.userService = userService;
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
        if (update == null) {
            return;
        }

        if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
            return;
        }

        if (!update.hasMessage()) {
            return;
        }

        Message message = update.getMessage();
        rememberUser(message);
        if (message.hasPhoto()) {
            handlePhoto(message);
            return;
        }
        if (!message.hasText()) {
            sendResponse(message.getChatId(), botDialogService.unsupportedMessage());
            return;
        }

        String incomingText = message.getText();
        String firstName = message.getFrom() == null ? null : message.getFrom().getFirstName();
        BotResponse response = botDialogService.handleText(message.getChatId(), incomingText, firstName);
        log.info("Received text message from chatId={}, text={}", message.getChatId(), incomingText);
        sendResponse(message.getChatId(), response);
    }

    @AfterBotRegistration
    public void onBotRegistered(BotSession botSession) {
        log.info("Telegram bot registered. sessionRunning={}", botSession.isRunning());
    }

    private void handleCallback(CallbackQuery callbackQuery) {
        if (callbackQuery.getMessage() == null) {
            return;
        }

        Long chatId = callbackQuery.getMessage().getChatId();
        rememberUser(chatId, callbackQuery.getFrom());
        String firstName = callbackQuery.getFrom() == null ? null : callbackQuery.getFrom().getFirstName();
        BotResponse response = botDialogService.handleCallback(chatId, callbackQuery.getData(), firstName);
        log.info("Received callback from chatId={}, data={}", chatId, callbackQuery.getData());
        sendResponse(chatId, response);
    }

    private void handlePhoto(Message message) {
        Optional<PhotoSize> photo = largestPhoto(message);
        if (photo.isEmpty()) {
            sendResponse(message.getChatId(), botDialogService.unsupportedMessage());
            return;
        }

        try (InputStream inputStream = telegramClient.downloadFileAsStream(photo.get().getFileId())) {
            MediaAsset mediaAsset = mediaAssetService.storeTelegramPhoto(
                    photo.get().getFileId(),
                    photo.get().getFileUniqueId(),
                    inputStream.readAllBytes(),
                    photo.get().getWidth(),
                    photo.get().getHeight(),
                    photo.get().getFileSize() == null ? null : photo.get().getFileSize().longValue()
            );
            String firstName = message.getFrom() == null ? null : message.getFrom().getFirstName();
            BotResponse response = botDialogService.handleMedia(message.getChatId(), mediaAsset, firstName);
            log.info("Received photo from chatId={}, mediaAssetId={}", message.getChatId(), mediaAsset.id());
            sendResponse(message.getChatId(), response);
        } catch (TelegramApiException | IOException | RuntimeException exception) {
            log.error("Failed to process Telegram photo from chatId={}", message.getChatId(), exception);
            sendResponse(message.getChatId(), BotResponse.of(
                    "Не смог сохранить фото. Проверь, что локально запущен MinIO, и попробуй еще раз.",
                    null
            ));
        }
    }

    private void rememberUser(Message message) {
        rememberUser(message.getChatId(), message.getFrom());
    }

    private void rememberUser(Long chatId, org.telegram.telegrambots.meta.api.objects.User user) {
        if (user == null) {
            return;
        }
        userService.rememberTelegramUser(chatId, user.getUserName(), user.getFirstName());
    }

    private Optional<PhotoSize> largestPhoto(Message message) {
        if (message.getPhoto() == null || message.getPhoto().isEmpty()) {
            return Optional.empty();
        }
        return message.getPhoto().stream()
                .max(Comparator.comparingInt(photo -> safe(photo.getWidth()) * safe(photo.getHeight())));
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private void sendResponse(Long chatId, BotResponse response) {
        if (response.photo() != null) {
            sendPhoto(chatId, response);
            return;
        }
        sendText(chatId, response);
    }

    private void sendText(Long chatId, BotResponse response) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(response.text())
                .build();
        ReplyKeyboard keyboard = response.keyboard();
        if (keyboard != null) {
            message.setReplyMarkup(keyboard);
        }

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException exception) {
            log.error("Failed to send Telegram message to chatId={}", chatId, exception);
        }
    }

    private void sendPhoto(Long chatId, BotResponse response) {
        MediaAsset mediaAsset = response.photo();
        String telegramFileId = mediaAsset.telegramFileId();
        if (telegramFileId != null && !telegramFileId.isBlank()) {
            sendPhoto(chatId, response, new InputFile(telegramFileId));
            return;
        }

        try (InputStream inputStream = objectStorageService.get(mediaAsset.storageKey())) {
            sendPhoto(chatId, response, new InputFile(inputStream, "card-" + mediaAsset.id() + ".jpg"));
        } catch (IOException | RuntimeException exception) {
            log.error("Failed to read media asset id={} for chatId={}", mediaAsset.id(), chatId, exception);
            sendText(chatId, BotResponse.of(response.text(), response.keyboard()));
        }
    }

    private void sendPhoto(Long chatId, BotResponse response, InputFile inputFile) {
        SendPhoto photo = new SendPhoto(chatId.toString(), inputFile);
        photo.setCaption(response.text());
        ReplyKeyboard keyboard = response.keyboard();
        if (keyboard != null) {
            photo.setReplyMarkup(keyboard);
        }

        try {
            telegramClient.execute(photo);
        } catch (TelegramApiException exception) {
            log.error("Failed to send Telegram photo to chatId={}", chatId, exception);
        }
    }
}
