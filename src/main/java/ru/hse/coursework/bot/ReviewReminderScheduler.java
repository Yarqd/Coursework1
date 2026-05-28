package ru.hse.coursework.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.hse.coursework.config.TelegramBotProperties;
import ru.hse.coursework.service.DeckService;
import ru.hse.coursework.service.ReviewReminderService;

import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnExpression("'${telegram.bot.token:}' != ''")
public class ReviewReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReviewReminderScheduler.class);
    private static final String MENU_STUDY = "menu:study";

    private final ReviewReminderService reviewReminderService;
    private final DeckService deckService;
    private final TelegramClient telegramClient;

    public ReviewReminderScheduler(
            ReviewReminderService reviewReminderService,
            DeckService deckService,
            TelegramBotProperties properties
    ) {
        this.reviewReminderService = reviewReminderService;
        this.deckService = deckService;
        this.telegramClient = new OkHttpTelegramClient(properties.token());
    }

    @Scheduled(
            initialDelayString = "${review-reminders.initial-delay:15000}",
            fixedDelayString = "${review-reminders.fixed-delay:60000}"
    )
    public void sendDueReminders() {
        Instant now = Instant.now();
        for (ReviewReminderService.ReminderCandidate candidate : reviewReminderService.pendingReminderCandidates(now)) {
            if (deckService.isActiveStudy(candidate.chatId(), now)) {
                continue;
            }
            try {
                telegramClient.execute(reminderMessage(candidate));
                reviewReminderService.markReminderSent(candidate.chatId(), candidate.dueCardSignature(), now);
                log.info("Sent review reminder to chatId={}, dueCards={}", candidate.chatId(), candidate.dueCards());
            } catch (TelegramApiException exception) {
                log.error("Failed to send review reminder to chatId={}", candidate.chatId(), exception);
            }
        }
    }

    private SendMessage reminderMessage(ReviewReminderService.ReminderCandidate candidate) {
        SendMessage message = SendMessage.builder()
                .chatId(candidate.chatId())
                .text("Привет, " + candidate.firstName() + ", пора повторить карточки, время пришло!")
                .build();
        InlineKeyboardButton studyButton = new InlineKeyboardButton("Изучать");
        studyButton.setCallbackData(MENU_STUDY);
        message.setReplyMarkup(new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(studyButton))));
        return message;
    }
}
