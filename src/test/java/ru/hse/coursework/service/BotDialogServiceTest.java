package ru.hse.coursework.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.hse.coursework.fsrs.FsrsCardState;
import ru.hse.coursework.persistence.entity.FsrsCardStateEntity;
import ru.hse.coursework.persistence.repository.FsrsCardStateRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(properties = "telegrambots.enabled=false")
class BotDialogServiceTest {
    @Autowired
    private DeckService deckService;

    @Autowired
    private BotDialogService dialogService;

    @Autowired
    private ReviewReminderService reviewReminderService;

    @Autowired
    private UserService userService;

    @Autowired
    private FsrsCardStateRepository fsrsCardStateRepository;

    @Test
    void startCommandShowsMainMenu() {
        BotResponse response = dialogService.handleText(101L, "/start", "Ярослав");

        assertThat(response.text()).contains("Привет, Ярослав");
        assertThat(response.text()).contains("Выбери действие");
        assertThat(response.keyboard()).isNotNull();
    }

    @Test
    void readyDeckCanBeAddedOnlyOnce() {
        long chatId = 102L;

        BotResponse firstResponse = dialogService.handleCallback(chatId, "ready:add:java-basics", "Ярослав");
        BotResponse secondResponse = dialogService.handleCallback(chatId, "ready:add:java-basics", "Ярослав");

        assertThat(firstResponse.text()).contains("Добавлено");
        assertThat(secondResponse.text()).contains("уже есть");
        assertThat(deckService.userDecks(chatId)).hasSize(1);
    }

    @Test
    void uploadedTextDeckIsAddedToUserDecks() {
        long chatId = 103L;

        BotResponse modePrompt = dialogService.handleCallback(chatId, "menu:upload", "Ярослав");
        assertThat(modePrompt.text()).contains("Выбери тип колоды");

        BotResponse titlePrompt = dialogService.handleCallback(chatId, "upload:text", "Ярослав");
        assertThat(titlePrompt.text()).contains("Шаг 1");

        BotResponse cardsPrompt = dialogService.handleText(chatId, "Тестовая колода", "Ярослав");
        assertThat(cardsPrompt.text()).contains("Шаг 2");

        BotResponse response = dialogService.handleText(
                chatId,
                """
                        question 1 :: answer 1
                        question 2 :: answer 2
                        """,
                "Ярослав"
        );

        assertThat(response.text()).contains("Колода загружена");
        assertThat(deckService.userDecks(chatId))
                .hasSize(1)
                .first()
                .satisfies(deck -> {
                    assertThat(deck.title()).isEqualTo("Тестовая колода");
                    assertThat(deck.cards()).hasSize(2);
                });
    }

    @Test
    void studyButtonExplainsWhenThereAreNoDueCards() {
        BotResponse response = dialogService.handleCallback(104L, "menu:study", "Ярослав");

        assertThat(response.text()).contains("На сейчас нет карточек");
    }

    @Test
    void deckCanBeEditedBySendingFullDeckTextBack() {
        long chatId = 105L;
        String deckId = deckService.addReadyDeckToUser(chatId, "java-basics").deck().id();

        BotResponse editResponse = dialogService.handleCallback(chatId, "mine:edit:" + deckId, "Ярослав");
        assertThat(editResponse.text())
                .contains("Редактирование колоды")
                .contains("Название: Java: основы")
                .contains("Какой тип используется для целых чисел?");

        BotResponse updatedResponse = dialogService.handleText(
                chatId,
                """
                        Название: Java минимум
                        Что такое JVM? :: Виртуальная машина Java.
                        Что такое record? :: Компактный способ описать неизменяемый носитель данных.
                        """,
                "Ярослав"
        );

        assertThat(updatedResponse.text()).contains("Колода обновлена").contains("Карточек: 2");
        assertThat(deckService.userDeck(chatId, deckId))
                .isPresent()
                .get()
                .satisfies(deck -> {
                    assertThat(deck.title()).isEqualTo("Java минимум");
                    assertThat(deck.cards()).hasSize(2);
                });
    }

    @Test
    void studyQuestionDoesNotExposeTechnicalFsrsState() {
        long chatId = 106L;
        String deckId = deckService.addReadyDeckToUser(chatId, "java-basics").deck().id();

        BotResponse response = dialogService.handleCallback(chatId, "study:start:" + deckId, "Ярослав");

        assertThat(response.text()).contains("Осталось до уверенного ответа");
        assertThat(response.text()).contains("Колода: Java: основы");
        assertThat(response.text()).doesNotContain("Прогресс:");
        assertThat(response.text()).doesNotContain("FSRS:");
    }

    @Test
    void studyButtonUsesNewAndDueCardsOnly() {
        long chatId = 109L;
        String deckId = deckService.addReadyDeckToUser(chatId, "java-basics").deck().id();

        BotResponse firstQuestion = dialogService.handleCallback(chatId, "menu:study", "Ярослав");
        assertThat(firstQuestion.text()).contains("Изучение: Карточки на сегодня");

        assertThat(deckService.gradeCurrentCard(chatId, DeckService.DUE_STUDY_ID, "good").finished()).isFalse();
        assertThat(deckService.gradeCurrentCard(chatId, DeckService.DUE_STUDY_ID, "good").finished()).isFalse();
        assertThat(deckService.gradeCurrentCard(chatId, DeckService.DUE_STUDY_ID, "good").finished()).isTrue();

        BotResponse noDueCards = dialogService.handleCallback(chatId, "study:start:" + deckId, "Ярослав");
        assertThat(noDueCards.text()).contains("сейчас нет карточек для изучения");
    }

    @Test
    void studySessionFinishesOnlyWhenEveryCardWasRemembered() {
        long chatId = 107L;
        String deckId = deckService.addReadyDeckToUser(chatId, "java-basics").deck().id();
        deckService.startStudy(chatId, deckId);

        DeckService.StudyProgress hardProgress = deckService.gradeCurrentCard(chatId, deckId, "hard");
        assertThat(hardProgress.finished()).isFalse();
        assertThat(hardProgress.remainingCards()).isEqualTo(3);

        assertThat(deckService.gradeCurrentCard(chatId, deckId, "good").finished()).isFalse();
        assertThat(deckService.gradeCurrentCard(chatId, deckId, "good").finished()).isFalse();

        DeckService.StudyProgress finalProgress = deckService.gradeCurrentCard(chatId, deckId, "good");
        assertThat(finalProgress.finished()).isTrue();
        assertThat(finalProgress.remainingCards()).isZero();
        assertThat(finalProgress.rememberedCards()).isEqualTo(3);
    }

    @Test
    void fsrsStateIsUpdatedAfterAnswerGrade() {
        long chatId = 108L;
        String deckId = deckService.addReadyDeckToUser(chatId, "java-basics").deck().id();
        deckService.startStudy(chatId, deckId);
        Long cardId = deckService.currentCard(chatId, deckId)
                .map(DeckService.CurrentCard::card)
                .map(card -> card.id())
                .orElseThrow();

        DeckService.StudyProgress progress = deckService.gradeCurrentCard(chatId, deckId, "bad");

        assertThat(progress.reviewResult()).isNotNull();
        assertThat(progress.reviewResult().scheduledDays()).isPositive();
        assertThat(deckService.cardState(chatId, cardId))
                .isPresent()
                .get()
                .satisfies(state -> {
                    assertThat(state.repetitions()).isEqualTo(1);
                    assertThat(state.lapses()).isEqualTo(1);
                    assertThat(state.difficulty()).isBetween(1.0, 10.0);
                    assertThat(state.stability()).isPositive();
                });
    }

    @Test
    void fsrsStateIsCalculatedOnlyByFirstRatingInSession() {
        long chatId = 111L;
        String deckId = deckService.addReadyDeckToUser(chatId, "java-basics").deck().id();
        deckService.startStudy(chatId, deckId);
        Long cardId = deckService.currentCard(chatId, deckId)
                .map(DeckService.CurrentCard::card)
                .map(card -> card.id())
                .orElseThrow();

        DeckService.StudyProgress firstProgress = deckService.gradeCurrentCard(chatId, deckId, "bad");
        FsrsCardState firstState = deckService.cardState(chatId, cardId).orElseThrow();

        assertThat(firstProgress.firstRatingInSession()).isTrue();
        assertThat(firstState.lapses()).isEqualTo(1);
        assertThat(firstState.repetitions()).isEqualTo(1);

        deckService.gradeCurrentCard(chatId, deckId, "good");
        deckService.gradeCurrentCard(chatId, deckId, "good");
        DeckService.StudyProgress repeatedCardProgress = deckService.gradeCurrentCard(chatId, deckId, "good");
        FsrsCardState finalState = deckService.cardState(chatId, cardId).orElseThrow();

        assertThat(repeatedCardProgress.firstRatingInSession()).isFalse();
        assertThat(repeatedCardProgress.reviewResult()).isNull();
        assertThat(finalState.lapses()).isEqualTo(1);
        assertThat(finalState.repetitions()).isEqualTo(1);
        assertThat(finalState.dueAt()).isEqualTo(firstState.dueAt());
    }

    @Test
    void dueReminderIsSentOnlyOnceUntilDueSetChangesOrIsAcknowledged() {
        long chatId = 110L;
        userService.rememberTelegramUser(chatId, "iaroslav", "Ярослав");
        String deckId = deckService.addReadyDeckToUser(chatId, "java-basics").deck().id();
        deckService.startStudy(chatId, deckId);
        Long cardId = deckService.currentCard(chatId, deckId)
                .map(DeckService.CurrentCard::card)
                .map(card -> card.id())
                .orElseThrow();
        deckService.gradeCurrentCard(chatId, deckId, "good");

        FsrsCardStateEntity state = fsrsCardStateRepository.findByChatIdAndCardId(chatId, cardId).orElseThrow();
        state.setDueAt(Instant.now().minusSeconds(5));
        fsrsCardStateRepository.save(state);

        List<ReviewReminderService.ReminderCandidate> candidates = reviewReminderService.pendingReminderCandidates(Instant.now());
        assertThat(candidates)
                .filteredOn(candidate -> candidate.chatId().equals(chatId))
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.firstName()).isEqualTo("Ярослав"));

        ReviewReminderService.ReminderCandidate candidate = candidates.stream()
                .filter(value -> value.chatId().equals(chatId))
                .findFirst()
                .orElseThrow();
        reviewReminderService.markReminderSent(chatId, candidate.dueCardSignature(), Instant.now());

        assertThat(reviewReminderService.pendingReminderCandidates(Instant.now()))
                .noneMatch(value -> value.chatId().equals(chatId));

        reviewReminderService.acknowledgeCurrentDue(chatId, Instant.now());
        assertThat(reviewReminderService.pendingReminderCandidates(Instant.now()))
                .noneMatch(value -> value.chatId().equals(chatId));
    }
}
