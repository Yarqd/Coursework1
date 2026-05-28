package ru.hse.coursework.service;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import ru.hse.coursework.model.CardSide;
import ru.hse.coursework.model.CardSideType;
import ru.hse.coursework.model.Deck;
import ru.hse.coursework.model.MediaAsset;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

@Service
public class BotDialogService {
    private static final String MENU_UPLOAD = "menu:upload";
    private static final String MENU_READY = "menu:ready";
    private static final String MENU_MINE = "menu:mine";
    private static final String MENU_STUDY = "menu:study";
    private static final String MENU_HOME = "menu:home";
    private static final String UPLOAD_TEXT = "upload:text";
    private static final String UPLOAD_IMAGE = "upload:image";
    private static final String IMAGE_NEXT = "image:next";
    private static final String IMAGE_FINISH = "image:finish";
    private static final String DECK_EDIT = "mine:edit:";

    private final DeckService deckService;
    private final ReviewReminderService reviewReminderService;

    public BotDialogService(DeckService deckService, ReviewReminderService reviewReminderService) {
        this.deckService = deckService;
        this.reviewReminderService = reviewReminderService;
    }

    public BotResponse handleText(Long chatId, String text, String firstName) {
        if (text == null || text.isBlank()) {
            return mainMenu(firstName);
        }

        String normalizedText = text.trim();
        if (deckService.isWaitingForUploadTitle(chatId) && !normalizedText.startsWith("/")) {
            return saveDeckTitle(chatId, normalizedText);
        }
        if (deckService.isWaitingForTextCards(chatId) && !normalizedText.startsWith("/")) {
            return importUserDeck(chatId, normalizedText);
        }
        if (deckService.isWaitingForImageQuestion(chatId) && !normalizedText.startsWith("/")) {
            return addImageQuestion(chatId, CardSide.text(normalizedText));
        }
        if (deckService.isWaitingForImageAnswer(chatId) && !normalizedText.startsWith("/")) {
            return addImageAnswer(chatId, CardSide.text(normalizedText));
        }
        if (deckService.isWaitingForImageNext(chatId) && !normalizedText.startsWith("/")) {
            return BotResponse.of("Карточка уже сохранена. Нажми «Добавить еще карточку» или «Завершить колоду».", imageNextKeyboard());
        }
        if (deckService.isWaitingForDeckEdit(chatId) && !normalizedText.startsWith("/")) {
            return updateUserDeck(chatId, normalizedText);
        }

        if ("/start".equalsIgnoreCase(normalizedText) || "/menu".equalsIgnoreCase(normalizedText)) {
            deckService.cancelPendingInput(chatId);
            return mainMenu(firstName);
        }
        if ("/help".equalsIgnoreCase(normalizedText)) {
            return help();
        }
        return BotResponse.of("Я пока работаю через кнопки. Нажми /menu, чтобы открыть главное меню.", mainMenuKeyboard());
    }

    public BotResponse handleMedia(Long chatId, MediaAsset mediaAsset, String firstName) {
        if (deckService.isWaitingForImageQuestion(chatId)) {
            return addImageQuestion(chatId, CardSide.image(mediaAsset));
        }
        if (deckService.isWaitingForImageAnswer(chatId)) {
            return addImageAnswer(chatId, CardSide.image(mediaAsset));
        }
        return BotResponse.of(
                "Фото получил, но сейчас не идет загрузка графической колоды. Нажми «Загрузить свою колоду» и выбери графический режим.",
                mainMenuKeyboard()
        );
    }

    public BotResponse handleCallback(Long chatId, String callbackData, String firstName) {
        if (callbackData == null || callbackData.isBlank() || MENU_HOME.equals(callbackData)) {
            deckService.cancelPendingInput(chatId);
            deckService.stopActiveStudy(chatId);
            return mainMenu(firstName);
        }

        if (MENU_UPLOAD.equals(callbackData)) {
            return uploadMode();
        }
        if (UPLOAD_TEXT.equals(callbackData)) {
            deckService.startTextUpload(chatId);
            return BotResponse.of(uploadTitleInstructions("текстовой"), backToMenuKeyboard());
        }
        if (UPLOAD_IMAGE.equals(callbackData)) {
            deckService.startImageUpload(chatId);
            return BotResponse.of(uploadTitleInstructions("графической"), backToMenuKeyboard());
        }
        if (IMAGE_NEXT.equals(callbackData)) {
            if (!deckService.beginNextImageCard(chatId)) {
                return BotResponse.of("Не нашел активную графическую колоду. Начни загрузку заново.", mainMenuKeyboard());
            }
            return BotResponse.of(imageQuestionInstructions(), backToMenuKeyboard());
        }
        if (IMAGE_FINISH.equals(callbackData)) {
            return finishImageDeck(chatId);
        }
        if (MENU_READY.equals(callbackData)) {
            return readyDecks();
        }
        if (MENU_MINE.equals(callbackData)) {
            return myDecks(chatId);
        }
        if (MENU_STUDY.equals(callbackData)) {
            return studyDueCards(chatId);
        }
        if (callbackData.startsWith("ready:add:")) {
            return addReadyDeck(chatId, callbackData.substring("ready:add:".length()));
        }
        if (callbackData.startsWith("mine:open:")) {
            return deckActions(chatId, callbackData.substring("mine:open:".length()));
        }
        if (callbackData.startsWith("mine:delete:")) {
            return deleteDeck(chatId, callbackData.substring("mine:delete:".length()));
        }
        if (callbackData.startsWith(DECK_EDIT)) {
            return editDeck(chatId, callbackData.substring(DECK_EDIT.length()));
        }
        if (callbackData.startsWith("study:start:")) {
            return startStudy(chatId, callbackData.substring("study:start:".length()));
        }
        if (callbackData.startsWith("study:show:")) {
            return showAnswer(chatId, callbackData.substring("study:show:".length()));
        }
        if (callbackData.startsWith("study:grade:")) {
            return gradeAnswer(chatId, callbackData.substring("study:grade:".length()));
        }
        return BotResponse.of("Не понял действие. Возвращаю в главное меню.", mainMenuKeyboard());
    }

    public BotResponse unsupportedMessage() {
        return BotResponse.of(
                "Пока я принимаю текст и фото. Фото используются при загрузке графической колоды: «Загрузить свою колоду» -> «Графическая колода».",
                mainMenuKeyboard()
        );
    }

    private BotResponse mainMenu(String firstName) {
        String name = firstName == null || firstName.isBlank() ? "друг" : firstName;
        return BotResponse.of(
                "Привет, " + name + "! Это бот для интервального повторения карточек.\n\n"
                        + "Повторения планируются по FSRS, а в текущей сессии карточка считается пройденной только после ответа «Помню».\n\n"
                        + "Выбери действие:",
                mainMenuKeyboard()
        );
    }

    private BotResponse help() {
        return BotResponse.of(
                "Основные ветки: загрузить свою колоду, взять готовую, посмотреть мои колоды, изучать актуальные карточки.\n\n"
                        + "Для своих колод есть два режима: текстовый формат «вопрос :: ответ» и графический режим, где вопросом или ответом может быть фото.",
                mainMenuKeyboard()
        );
    }

    private BotResponse uploadMode() {
        return BotResponse.of(
                "Выбери тип колоды.\n\n"
                        + "Текстовая: все карточки одним сообщением в формате «вопрос :: ответ».\n"
                        + "Графическая: добавляем карточки по одной, вопрос и ответ могут быть текстом или фото.",
                keyboard(List.of(
                        row(button("Текстовая колода", UPLOAD_TEXT)),
                        row(button("Графическая колода", UPLOAD_IMAGE)),
                        row(button("Главное меню", MENU_HOME))
                ))
        );
    }

    private BotResponse readyDecks() {
        List<Deck> decks = deckService.readyDecks();
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Deck deck : decks) {
            rows.add(row(button(deck.title() + " (" + deck.size() + ")", "ready:add:" + deck.id())));
        }
        rows.add(row(button("Назад в меню", MENU_HOME)));
        return BotResponse.of("Готовые колоды. Выбери колоду, чтобы добавить ее себе:", keyboard(rows));
    }

    private BotResponse addReadyDeck(Long chatId, String readyDeckId) {
        DeckService.AddReadyDeckResult result = deckService.addReadyDeckToUser(chatId, readyDeckId);
        if (result.status() == DeckService.AddReadyDeckStatus.ADDED) {
            return BotResponse.of(
                    "Добавлено: " + result.deck().title() + ". Можно сразу начать изучение.",
                    deckActionKeyboard(result.deck().id())
            );
        }
        if (result.status() == DeckService.AddReadyDeckStatus.ALREADY_EXISTS) {
            return BotResponse.of(
                    "У вас уже есть эта колода: " + result.deck().title() + ".",
                    deckActionKeyboard(result.deck().id())
            );
        }
        return BotResponse.of("Не нашел такую готовую колоду.", readyDecksKeyboard());
    }

    private BotResponse myDecks(Long chatId) {
        List<Deck> decks = deckService.userDecks(chatId);
        if (decks.isEmpty()) {
            return BotResponse.of(
                    "У тебя пока нет колод. Можно загрузить свою или взять готовую.",
                    mainMenuKeyboard()
            );
        }

        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Deck deck : decks) {
            rows.add(row(button(deck.title() + " (" + deck.size() + ")", "mine:open:" + deck.id())));
        }
        rows.add(row(button("Назад в меню", MENU_HOME)));
        return BotResponse.of("Мои колоды. Выбери колоду:", keyboard(rows));
    }

    private BotResponse deckActions(Long chatId, String deckId) {
        Optional<Deck> deck = deckService.userDeck(chatId, deckId);
        if (deck.isEmpty()) {
            return BotResponse.of("Колода не найдена.", mainMenuKeyboard());
        }
        return BotResponse.of(
                "Колода: " + deck.get().title() + "\nКарточек: " + deck.get().size() + "\n\n"
                        + "Можно начать изучение, отредактировать список карточек или удалить колоду.",
                deckActionKeyboard(deckId)
        );
    }

    private BotResponse deleteDeck(Long chatId, String deckId) {
        boolean deleted = deckService.deleteUserDeck(chatId, deckId);
        if (!deleted) {
            return BotResponse.of("Колода уже удалена или не найдена.", mainMenuKeyboard());
        }
        return BotResponse.of("Удалена.", mainMenuKeyboard());
    }

    private BotResponse editDeck(Long chatId, String deckId) {
        Optional<Deck> deck = deckService.startDeckEdit(chatId, deckId);
        if (deck.isEmpty()) {
            return BotResponse.of("Колода не найдена.", mainMenuKeyboard());
        }

        return BotResponse.of(
                editInstructions(deck.get().title(), deckService.deckAsEditableText(chatId, deckId)),
                backToMenuKeyboard()
        );
    }

    private BotResponse studyDueCards(Long chatId) {
        reviewReminderService.acknowledgeCurrentDue(chatId, Instant.now());
        DeckService.StudyStartResult result = deckService.startDueStudy(chatId);
        if (result.status() == DeckService.StudyStartStatus.NO_CARDS_DUE) {
            return BotResponse.of(
                    "На сейчас нет карточек для изучения. Новые карточки и карточки с наступившим сроком появятся здесь.",
                    mainMenuKeyboard()
            );
        }
        return studyQuestion(chatId, result.studyId());
    }

    private BotResponse startStudy(Long chatId, String deckId) {
        DeckService.StudyStartResult result = deckService.startStudy(chatId, deckId);
        if (result.status() == DeckService.StudyStartStatus.NOT_FOUND) {
            return BotResponse.of("Не получилось начать изучение: колода не найдена.", mainMenuKeyboard());
        }
        if (result.status() == DeckService.StudyStartStatus.NO_CARDS_DUE) {
            return BotResponse.of(
                    "В этой колоде сейчас нет карточек для изучения. Я покажу их, когда наступит срок повторения.",
                    deckActionKeyboard(deckId)
            );
        }
        return studyQuestion(chatId, result.studyId());
    }

    private BotResponse studyQuestion(Long chatId, String studyId) {
        Optional<DeckService.CurrentCard> card = deckService.currentCard(chatId, studyId);
        if (card.isEmpty()) {
            return BotResponse.of("Сейчас нет активной учебной сессии. Нажми «Изучать», чтобы начать.", mainMenuKeyboard());
        }
        DeckService.CurrentCard currentCard = card.get();
        String caption = "Изучение: " + currentCard.studyTitle()
                + "\nКолода: " + currentCard.deckTitle()
                + "\nОсталось до уверенного ответа: " + currentCard.remainingCards()
                + "\n\nВопрос:";
        return sideResponse(currentCard.card().question(), caption, keyboard(List.of(
                row(button("Показать ответ", "study:show:" + studyId)),
                row(button("Мои колоды", MENU_MINE), button("Главное меню", MENU_HOME))
        )));
    }

    private BotResponse showAnswer(Long chatId, String deckId) {
        Optional<DeckService.CurrentCard> card = deckService.currentCard(chatId, deckId);
        if (card.isEmpty()) {
            return BotResponse.of("Карточка не найдена.", mainMenuKeyboard());
        }
        return sideResponse(
                card.get().card().answer(),
                "Ответ:",
                keyboard(List.of(
                        row(button("Помню", "study:grade:" + deckId + ":good")),
                        row(button("Сложно", "study:grade:" + deckId + ":hard")),
                        row(button("Не помню", "study:grade:" + deckId + ":bad"))
                ))
        );
    }

    private BotResponse gradeAnswer(Long chatId, String payload) {
        String[] parts = payload.split(":", 2);
        if (parts.length != 2) {
            return BotResponse.of("Не понял оценку ответа.", mainMenuKeyboard());
        }

        DeckService.StudyProgress progress = deckService.gradeCurrentCard(chatId, parts[0], parts[1]);
        if (progress.finished()) {
            return BotResponse.of(
                    "Сессия завершена: все карточки получили ответ «Помню».\n"
                            + reviewResultLine(progress),
                    keyboard(List.of(
                            row(button("Изучать еще", MENU_STUDY)),
                            row(button("Мои колоды", MENU_MINE), button("Главное меню", MENU_HOME))
                    ))
            );
        }
        BotResponse nextQuestion = studyQuestion(chatId, parts[0]);
        return nextQuestion.withText(reviewResultLine(progress) + "\n\n" + nextQuestion.text());
    }

    private BotResponse importUserDeck(Long chatId, String payload) {
        DeckService.ImportDeckResult result = deckService.importDeck(chatId, payload);
        if (!result.success()) {
            return BotResponse.of(result.message(), backToMenuKeyboard());
        }
        return BotResponse.of(
                "Колода загружена: " + result.deck().title() + "\nКарточек: " + result.deck().size(),
                deckActionKeyboard(result.deck().id())
        );
    }

    private BotResponse finishImageDeck(Long chatId) {
        DeckService.ImportDeckResult result = deckService.finishImageDeck(chatId);
        if (!result.success()) {
            return BotResponse.of(result.message(), imageNextKeyboard());
        }
        return BotResponse.of(
                "Графическая колода загружена: " + result.deck().title() + "\nКарточек: " + result.deck().size(),
                deckActionKeyboard(result.deck().id())
        );
    }

    private BotResponse saveDeckTitle(Long chatId, String title) {
        if (!deckService.saveUploadTitle(chatId, title)) {
            return BotResponse.of(
                    "Название не должно быть пустым. Отправь, пожалуйста, короткое название колоды одним сообщением.",
                    backToMenuKeyboard()
            );
        }
        if (deckService.isWaitingForTextCards(chatId)) {
            return BotResponse.of(
                    "Название сохранено: " + title + "\n\n" + uploadCardsInstructions(),
                    backToMenuKeyboard()
            );
        }
        return BotResponse.of(
                "Название сохранено: " + title + "\n\n" + imageQuestionInstructions(),
                backToMenuKeyboard()
        );
    }

    private BotResponse updateUserDeck(Long chatId, String payload) {
        DeckService.ImportDeckResult result = deckService.updateDeckFromText(chatId, payload);
        if (!result.success()) {
            return BotResponse.of(result.message() + "\n\nПопробуй отправить исправленный текст еще раз.", backToMenuKeyboard());
        }
        return BotResponse.of(
                "Колода обновлена: " + result.deck().title() + "\nКарточек: " + result.deck().size() + "\n\n"
                        + "Прогресс по этой колоде сброшен, потому что состав карточек изменился.",
                deckActionKeyboard(result.deck().id())
        );
    }

    private BotResponse addImageQuestion(Long chatId, CardSide question) {
        DeckService.AddImageCardResult result = deckService.addImageQuestion(chatId, question);
        if (!result.success()) {
            return BotResponse.of(result.message(), mainMenuKeyboard());
        }
        return BotResponse.of(result.message(), backToMenuKeyboard());
    }

    private BotResponse addImageAnswer(Long chatId, CardSide answer) {
        DeckService.AddImageCardResult result = deckService.addImageAnswer(chatId, answer);
        if (!result.success()) {
            return BotResponse.of(result.message(), mainMenuKeyboard());
        }
        return BotResponse.of(result.message(), imageNextKeyboard());
    }

    private BotResponse sideResponse(CardSide side, String caption, InlineKeyboardMarkup keyboard) {
        if (side.type() == CardSideType.IMAGE && side.mediaAsset() != null) {
            return BotResponse.photo(side.mediaAsset(), caption, keyboard);
        }
        return BotResponse.of(caption + "\n" + side.text(), keyboard);
    }

    private String uploadTitleInstructions(String deckType) {
        return """
                Шаг 1. Отправь название %s колоды одним сообщением.

                Например:
                Java Core
                """.formatted(deckType);
    }

    private String uploadCardsInstructions() {
        return """
                Шаг 2. Теперь отправь карточки одним сообщением.

                Формат: вопрос :: ответ
                Каждая карточка должна быть на отдельной строке.

                Например:
                apple :: яблоко
                spring :: весна
                card :: карточка

                Чтобы отменить загрузку, нажми «Главное меню».
                """;
    }

    private String imageQuestionInstructions() {
        return """
                Шаг 2. Отправь вопрос первой карточки.

                Это может быть текст или фото. После этого я попрошу отправить ответ: тоже текстом или фото.
                """;
    }

    private String editInstructions(String deckTitle, String editableDeckText) {
        return """
                Редактирование колоды: %s

                Скопируй текст ниже, измени его и отправь обратно одним сообщением.
                - чтобы добавить текстовую карточку, добавь новую строку в формате: вопрос :: ответ;
                - чтобы удалить карточку, удали лишнюю строку;
                - чтобы переименовать колоду, измени строку «Название: ...».

                Если в колоде есть картинки, они будут показаны как [image:номер]. Эти строки можно оставлять или удалять, но новые картинки через текстовое редактирование пока не добавляются.

                %s
                """.formatted(deckTitle, editableDeckText);
    }

    private InlineKeyboardMarkup mainMenuKeyboard() {
        return keyboard(List.of(
                row(button("Загрузить свою колоду", MENU_UPLOAD)),
                row(button("Взять готовую колоду", MENU_READY)),
                row(button("Мои колоды", MENU_MINE)),
                row(button("Изучать", MENU_STUDY))
        ));
    }

    private InlineKeyboardMarkup readyDecksKeyboard() {
        return keyboard(List.of(
                row(button("К готовым колодам", MENU_READY)),
                row(button("Главное меню", MENU_HOME))
        ));
    }

    private InlineKeyboardMarkup deckActionKeyboard(String deckId) {
        return keyboard(List.of(
                row(button("Изучать", "study:start:" + deckId)),
                row(button("Редактировать колоду", DECK_EDIT + deckId)),
                row(button("Удалить колоду", "mine:delete:" + deckId)),
                row(button("Мои колоды", MENU_MINE), button("Главное меню", MENU_HOME))
        ));
    }

    private InlineKeyboardMarkup imageNextKeyboard() {
        return keyboard(List.of(
                row(button("Добавить еще карточку", IMAGE_NEXT)),
                row(button("Завершить колоду", IMAGE_FINISH)),
                row(button("Главное меню", MENU_HOME))
        ));
    }

    private InlineKeyboardMarkup backToMenuKeyboard() {
        return keyboard(List.of(row(button("Главное меню", MENU_HOME))));
    }

    private InlineKeyboardMarkup keyboard(List<InlineKeyboardRow> rows) {
        return new InlineKeyboardMarkup(rows);
    }

    private InlineKeyboardRow row(InlineKeyboardButton... buttons) {
        return new InlineKeyboardRow(buttons);
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton(text);
        button.setCallbackData(callbackData);
        return button;
    }

    private String reviewResultLine(DeckService.StudyProgress progress) {
        if (!progress.firstRatingInSession()) {
            return "Карточка закреплена в этой сессии. Интервал уже был рассчитан по первому ответу; осталось "
                    + progress.remainingCards()
                    + ".";
        }
        if (progress.reviewResult() == null) {
            return "Результат повторения сохранен.";
        }
        return "Ответ сохранен. Следующее плановое повторение примерно через "
                + progress.reviewResult().scheduledDays()
                + " дн.; осталось в этой сессии "
                + progress.remainingCards()
                + ".";
    }
}
