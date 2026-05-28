package ru.hse.coursework.service;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hse.coursework.fsrs.FsrsCardState;
import ru.hse.coursework.fsrs.FsrsRating;
import ru.hse.coursework.fsrs.FsrsReviewResult;
import ru.hse.coursework.fsrs.FsrsScheduler;
import ru.hse.coursework.model.CardSide;
import ru.hse.coursework.model.CardSideType;
import ru.hse.coursework.model.Deck;
import ru.hse.coursework.model.DeckSource;
import ru.hse.coursework.model.MediaAsset;
import ru.hse.coursework.model.StudyCard;
import ru.hse.coursework.persistence.entity.CardEntity;
import ru.hse.coursework.persistence.entity.DeckEntity;
import ru.hse.coursework.persistence.entity.FsrsCardStateEntity;
import ru.hse.coursework.persistence.entity.MediaAssetEntity;
import ru.hse.coursework.persistence.entity.ReviewLogEntity;
import ru.hse.coursework.persistence.repository.DeckRepository;
import ru.hse.coursework.persistence.repository.FsrsCardStateRepository;
import ru.hse.coursework.persistence.repository.MediaAssetRepository;
import ru.hse.coursework.persistence.repository.ReviewLogRepository;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeckService {
    public static final String DUE_STUDY_ID = "__due__";
    private static final Duration ACTIVE_STUDY_TIMEOUT = Duration.ofMinutes(30);

    private final DeckRepository deckRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final FsrsCardStateRepository fsrsCardStateRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final FsrsScheduler fsrsScheduler;
    private final Map<Long, String> lastDeckByChat = new ConcurrentHashMap<>();
    private final Map<StudySessionKey, StudySession> studySessions = new ConcurrentHashMap<>();
    private final Map<Long, DeckDraft> deckDrafts = new ConcurrentHashMap<>();
    private final Map<Long, String> editingDecks = new ConcurrentHashMap<>();

    public DeckService(
            DeckRepository deckRepository,
            MediaAssetRepository mediaAssetRepository,
            FsrsCardStateRepository fsrsCardStateRepository,
            ReviewLogRepository reviewLogRepository,
            FsrsScheduler fsrsScheduler
    ) {
        this.deckRepository = deckRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.fsrsCardStateRepository = fsrsCardStateRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.fsrsScheduler = fsrsScheduler;
    }

    @PostConstruct
    void seedReadyDecks() {
        seedReadyDeck("java-basics", "Java: основы", List.of(
                StudyCard.text("Какой тип используется для целых чисел?", "int, long, short или byte в зависимости от диапазона."),
                StudyCard.text("Что такое JVM?", "Виртуальная машина Java, выполняющая байткод."),
                StudyCard.text("Что означает ключевое слово final?", "Запрещает переопределение значения, метода или наследование класса в зависимости от контекста.")
        ));
        seedReadyDeck("oop", "ООП: термины", List.of(
                StudyCard.text("Что такое инкапсуляция?", "Сокрытие внутреннего состояния объекта и управление доступом через методы."),
                StudyCard.text("Что такое наследование?", "Механизм создания класса на основе другого класса."),
                StudyCard.text("Что такое полиморфизм?", "Возможность работать с объектами разных классов через общий интерфейс или базовый тип.")
        ));
        seedReadyDeck("english-it", "English IT", List.of(
                StudyCard.text("deployment", "развертывание"),
                StudyCard.text("storage", "хранилище"),
                StudyCard.text("scheduler", "планировщик")
        ));
    }

    @Transactional(readOnly = true)
    public List<Deck> readyDecks() {
        return deckRepository.findBySourceAndOwnerChatIdIsNullOrderByTitle(DeckSource.READY)
                .stream()
                .map(this::toDeck)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Deck> userDecks(Long chatId) {
        return deckRepository.findByOwnerChatIdOrderByCreatedAtAsc(chatId)
                .stream()
                .map(this::toDeck)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Deck> userDeck(Long chatId, String deckId) {
        return deckRepository.findWithCardsById(deckId)
                .filter(deck -> chatId.equals(deck.getOwnerChatId()))
                .map(this::toDeck);
    }

    @Transactional
    public AddReadyDeckResult addReadyDeckToUser(Long chatId, String readyDeckId) {
        Optional<DeckEntity> readyDeck = deckRepository.findWithCardsById(readyDeckId)
                .filter(deck -> deck.getSource() == DeckSource.READY && deck.getOwnerChatId() == null);
        if (readyDeck.isEmpty()) {
            return new AddReadyDeckResult(AddReadyDeckStatus.NOT_FOUND, null);
        }

        Optional<DeckEntity> existingDeck = deckRepository.findByOwnerChatIdAndReadySourceId(chatId, readyDeckId);
        if (existingDeck.isPresent()) {
            return new AddReadyDeckResult(AddReadyDeckStatus.ALREADY_EXISTS, toDeck(existingDeck.get()));
        }

        DeckEntity userDeck = copyReadyDeck(chatId, readyDeck.get());
        deckRepository.save(userDeck);
        return new AddReadyDeckResult(AddReadyDeckStatus.ADDED, toDeck(userDeck));
    }

    @Transactional
    public boolean deleteUserDeck(Long chatId, String deckId) {
        Optional<DeckEntity> deck = deckRepository.findById(deckId).filter(value -> chatId.equals(value.getOwnerChatId()));
        if (deck.isEmpty()) {
            return false;
        }
        deckRepository.delete(deck.get());
        if (deckId.equals(lastDeckByChat.get(chatId))) {
            lastDeckByChat.remove(chatId);
        }
        studySessions.remove(new StudySessionKey(chatId, deckId));
        return true;
    }

    public void startTextUpload(Long chatId) {
        startDraft(chatId, UploadMode.TEXT);
    }

    public void startImageUpload(Long chatId) {
        startDraft(chatId, UploadMode.IMAGE);
    }

    public boolean isWaitingForUploadTitle(Long chatId) {
        return deckDrafts.containsKey(chatId) && deckDrafts.get(chatId).step() == DraftStep.TITLE;
    }

    public boolean isWaitingForTextCards(Long chatId) {
        return deckDrafts.containsKey(chatId) && deckDrafts.get(chatId).step() == DraftStep.TEXT_CARDS;
    }

    public boolean isWaitingForImageQuestion(Long chatId) {
        return deckDrafts.containsKey(chatId) && deckDrafts.get(chatId).step() == DraftStep.IMAGE_QUESTION;
    }

    public boolean isWaitingForImageAnswer(Long chatId) {
        return deckDrafts.containsKey(chatId) && deckDrafts.get(chatId).step() == DraftStep.IMAGE_ANSWER;
    }

    public boolean isWaitingForImageNext(Long chatId) {
        return deckDrafts.containsKey(chatId) && deckDrafts.get(chatId).step() == DraftStep.IMAGE_NEXT;
    }

    public boolean saveUploadTitle(Long chatId, String title) {
        DeckDraft draft = deckDrafts.get(chatId);
        String normalizedTitle = normalizeTitle(title);
        if (draft == null || normalizedTitle.isBlank()) {
            return false;
        }
        draft.setTitle(normalizedTitle);
        draft.setStep(draft.mode() == UploadMode.TEXT ? DraftStep.TEXT_CARDS : DraftStep.IMAGE_QUESTION);
        return true;
    }

    public boolean isWaitingForDeckEdit(Long chatId) {
        return editingDecks.containsKey(chatId);
    }

    @Transactional(readOnly = true)
    public Optional<Deck> startDeckEdit(Long chatId, String deckId) {
        Optional<Deck> deck = userDeck(chatId, deckId);
        deck.ifPresent(value -> {
            deckDrafts.remove(chatId);
            editingDecks.put(chatId, value.id());
        });
        return deck;
    }

    public void cancelPendingInput(Long chatId) {
        deckDrafts.remove(chatId);
        editingDecks.remove(chatId);
    }

    @Transactional
    public ImportDeckResult importDeck(Long chatId, String payload) {
        DeckDraft draft = deckDrafts.get(chatId);
        String title = draft == null || draft.title() == null ? "Моя колода" : draft.title();
        ParsedDeck parsedDeck = parseDeck(payload, title);
        if (parsedDeck.cards().isEmpty()) {
            return ImportDeckResult.failure("Не смог найти карточки. Используй формат: вопрос :: ответ, по одной карточке на строку.");
        }

        DeckEntity deck = new DeckEntity();
        deck.setId(newUserDeckId());
        deck.setOwnerChatId(chatId);
        deck.setTitle(parsedDeck.title());
        deck.setSource(DeckSource.USER);
        deck.replaceCards(parsedDeck.cards().stream().map(this::toCardEntity).toList());
        deckRepository.save(deck);
        deckDrafts.remove(chatId);
        return ImportDeckResult.success(toDeck(deck));
    }

    public AddImageCardResult addImageQuestion(Long chatId, CardSide question) {
        DeckDraft draft = deckDrafts.get(chatId);
        if (draft == null || draft.mode() != UploadMode.IMAGE) {
            return AddImageCardResult.failure("Сейчас нет активной загрузки графической колоды.");
        }
        draft.setPendingQuestion(question);
        draft.setStep(DraftStep.IMAGE_ANSWER);
        return AddImageCardResult.success("Вопрос сохранен. Теперь отправь ответ: текст или картинку.");
    }

    public AddImageCardResult addImageAnswer(Long chatId, CardSide answer) {
        DeckDraft draft = deckDrafts.get(chatId);
        if (draft == null || draft.mode() != UploadMode.IMAGE || draft.pendingQuestion() == null) {
            return AddImageCardResult.failure("Сначала нужно отправить вопрос карточки.");
        }
        draft.cards().add(new StudyCard(null, draft.pendingQuestion(), answer));
        draft.setPendingQuestion(null);
        draft.setStep(DraftStep.IMAGE_NEXT);
        return AddImageCardResult.success("Карточка добавлена. Всего карточек в колоде: " + draft.cards().size() + ".");
    }

    public boolean beginNextImageCard(Long chatId) {
        DeckDraft draft = deckDrafts.get(chatId);
        if (draft == null || draft.mode() != UploadMode.IMAGE) {
            return false;
        }
        draft.setStep(DraftStep.IMAGE_QUESTION);
        return true;
    }

    @Transactional
    public ImportDeckResult finishImageDeck(Long chatId) {
        DeckDraft draft = deckDrafts.get(chatId);
        if (draft == null || draft.mode() != UploadMode.IMAGE) {
            return ImportDeckResult.failure("Нет активной графической колоды.");
        }
        if (draft.cards().isEmpty()) {
            return ImportDeckResult.failure("Нужно добавить хотя бы одну карточку.");
        }

        DeckEntity deck = new DeckEntity();
        deck.setId(newUserDeckId());
        deck.setOwnerChatId(chatId);
        deck.setTitle(draft.title());
        deck.setSource(DeckSource.USER);
        deck.replaceCards(draft.cards().stream().map(this::toCardEntity).toList());
        deckRepository.save(deck);
        deckDrafts.remove(chatId);
        return ImportDeckResult.success(toDeck(deck));
    }

    @Transactional
    public ImportDeckResult updateDeckFromText(Long chatId, String payload) {
        String deckId = editingDecks.get(chatId);
        if (deckId == null) {
            return ImportDeckResult.failure("Сейчас нет колоды в режиме редактирования.");
        }

        Optional<DeckEntity> currentDeck = deckRepository.findWithCardsById(deckId)
                .filter(deck -> chatId.equals(deck.getOwnerChatId()));
        if (currentDeck.isEmpty()) {
            editingDecks.remove(chatId);
            return ImportDeckResult.failure("Колода не найдена. Возможно, она уже была удалена.");
        }

        ParsedDeck parsedDeck = parseDeck(payload, currentDeck.get().getTitle());
        if (parsedDeck.cards().isEmpty()) {
            return ImportDeckResult.failure("После редактирования не осталось карточек. Оставь хотя бы одну строку в формате: вопрос :: ответ.");
        }

        DeckEntity deck = currentDeck.get();
        List<Long> oldCardIds = deck.getCards().stream().map(CardEntity::getId).toList();
        deck.setTitle(parsedDeck.title());
        deck.replaceCards(parsedDeck.cards().stream().map(this::toCardEntity).toList());
        deckRepository.save(deck);
        resetDeckProgress(chatId, deck.getId());
        fsrsCardStateRepository.deleteByChatIdAndCardIdIn(chatId, oldCardIds);
        editingDecks.remove(chatId);
        return ImportDeckResult.success(toDeck(deck));
    }

    @Transactional(readOnly = true)
    public String deckAsEditableText(Long chatId, String deckId) {
        return userDeck(chatId, deckId)
                .map(deck -> {
                    StringBuilder builder = new StringBuilder("Название: ").append(deck.title());
                    for (StudyCard card : deck.cards()) {
                        builder.append(System.lineSeparator())
                                .append(card.question().displayText())
                                .append(" :: ")
                                .append(card.answer().displayText());
                    }
                    return builder.toString();
                })
                .orElse("");
    }

    @Transactional
    public StudyStartResult startStudy(Long chatId, String deckId) {
        Optional<Deck> deck = userDeck(chatId, deckId);
        if (deck.isEmpty()) {
            return StudyStartResult.notFound();
        }

        Instant now = Instant.now();
        List<StudyItem> items = studyItemsForDeck(chatId, deck.get(), now);
        if (items.isEmpty()) {
            studySessions.remove(new StudySessionKey(chatId, deckId));
            return StudyStartResult.noCardsDue(deck.get().title());
        }

        lastDeckByChat.put(chatId, deck.get().id());
        studySessions.put(new StudySessionKey(chatId, deck.get().id()), StudySession.start(deck.get().title(), items, now));
        return StudyStartResult.started(deck.get().id(), deck.get().title(), items.size());
    }

    @Transactional
    public StudyStartResult startDueStudy(Long chatId) {
        Instant now = Instant.now();
        List<StudyItem> items = userDecks(chatId).stream()
                .flatMap(deck -> studyItemsForDeck(chatId, deck, now).stream())
                .toList();
        if (items.isEmpty()) {
            studySessions.remove(new StudySessionKey(chatId, DUE_STUDY_ID));
            return StudyStartResult.noCardsDue("Карточки на сегодня");
        }

        studySessions.put(new StudySessionKey(chatId, DUE_STUDY_ID), StudySession.start("Карточки на сегодня", items, now));
        return StudyStartResult.started(DUE_STUDY_ID, "Карточки на сегодня", items.size());
    }

    public void stopActiveStudy(Long chatId) {
        studySessions.keySet().removeIf(key -> key.chatId().equals(chatId));
    }

    public boolean isActiveStudy(Long chatId, Instant now) {
        return studySessions.entrySet().stream()
                .filter(entry -> entry.getKey().chatId().equals(chatId))
                .anyMatch(entry -> entry.getValue().isActive(now));
    }

    @Transactional(readOnly = true)
    public Optional<Deck> lastDeck(Long chatId) {
        return Optional.ofNullable(lastDeckByChat.get(chatId)).flatMap(deckId -> userDeck(chatId, deckId));
    }

    @Transactional(readOnly = true)
    public Optional<CurrentCard> currentCard(Long chatId, String studyId) {
        StudySessionKey key = new StudySessionKey(chatId, studyId);
        StudySession session = studySessions.get(key);
        if (session == null) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        session.touch(now);
        StudyItem currentItem = session.currentItem();
        if (currentItem == null) {
            return Optional.empty();
        }

        FsrsCardState state = fsrsCardStateRepository.findByChatIdAndCardId(chatId, currentItem.card().id())
                .map(this::toFsrsState)
                .orElseGet(() -> FsrsCardState.newCard(now));
        return Optional.of(new CurrentCard(
                currentItem.card(),
                currentItem.deckId(),
                currentItem.deckTitle(),
                session.studyTitle(),
                session.rememberedCount(),
                session.totalCards(),
                session.remainingCount(),
                state
        ));
    }

    @Transactional
    public StudyProgress gradeCurrentCard(Long chatId, String studyId, String grade) {
        StudySessionKey key = new StudySessionKey(chatId, studyId);
        StudySession session = studySessions.get(key);
        if (session == null) {
            return StudyProgress.empty();
        }

        StudyItem currentItem = session.currentItem();
        if (currentItem == null) {
            studySessions.remove(key);
            return StudyProgress.finished(session.totalCards(), null);
        }

        FsrsRating rating = FsrsRating.fromCallbackGrade(grade);
        Instant now = Instant.now();
        FsrsReviewResult reviewResult = fsrsScheduler.review(
                fsrsCardStateRepository.findByChatIdAndCardId(chatId, currentItem.card().id())
                        .map(this::toFsrsState)
                        .orElseGet(() -> FsrsCardState.newCard(now)),
                rating,
                now
        );
        saveFsrsState(chatId, currentItem.card().id(), reviewResult.state());
        saveReviewLog(chatId, currentItem.card().id(), rating, now, reviewResult);

        boolean finished = session.apply(rating, now);
        lastDeckByChat.put(chatId, currentItem.deckId());
        if (finished) {
            studySessions.remove(key);
            return StudyProgress.finished(session.totalCards(), reviewResult);
        }
        return new StudyProgress(false, session.rememberedCount(), session.totalCards(), session.remainingCount(), reviewResult);
    }

    @Transactional(readOnly = true)
    public Optional<FsrsCardState> cardState(Long chatId, Long cardId) {
        return fsrsCardStateRepository.findByChatIdAndCardId(chatId, cardId).map(this::toFsrsState);
    }

    private List<StudyItem> studyItemsForDeck(Long chatId, Deck deck, Instant now) {
        return deck.cards().stream()
                .filter(card -> isAvailableForStudy(chatId, card, now))
                .map(card -> new StudyItem(deck.id(), deck.title(), card))
                .toList();
    }

    private boolean isAvailableForStudy(Long chatId, StudyCard card, Instant now) {
        return fsrsCardStateRepository.findByChatIdAndCardId(chatId, card.id())
                .map(state -> !state.getDueAt().isAfter(now))
                .orElse(true);
    }

    private void seedReadyDeck(String id, String title, List<StudyCard> cards) {
        if (deckRepository.existsById(id)) {
            return;
        }
        DeckEntity deck = new DeckEntity();
        deck.setId(id);
        deck.setTitle(title);
        deck.setSource(DeckSource.READY);
        deck.replaceCards(cards.stream().map(this::toCardEntity).toList());
        deckRepository.save(deck);
    }

    private void startDraft(Long chatId, UploadMode mode) {
        editingDecks.remove(chatId);
        deckDrafts.put(chatId, DeckDraft.start(mode));
    }

    private void resetDeckProgress(Long chatId, String deckId) {
        studySessions.remove(new StudySessionKey(chatId, deckId));
    }

    private DeckEntity copyReadyDeck(Long chatId, DeckEntity readyDeck) {
        DeckEntity deck = new DeckEntity();
        deck.setId(readyUserDeckId(chatId, readyDeck.getId()));
        deck.setOwnerChatId(chatId);
        deck.setTitle(readyDeck.getTitle());
        deck.setSource(DeckSource.USER);
        deck.setReadySourceId(readyDeck.getId());
        deck.replaceCards(readyDeck.getCards().stream()
                .map(this::toStudyCard)
                .map(this::toCardEntity)
                .toList());
        return deck;
    }

    private CardEntity toCardEntity(StudyCard card) {
        CardEntity entity = new CardEntity();
        entity.setQuestionType(card.question().type());
        entity.setQuestionText(card.question().text());
        entity.setQuestionMedia(toMediaEntity(card.question().mediaAsset()));
        entity.setAnswerType(card.answer().type());
        entity.setAnswerText(card.answer().text());
        entity.setAnswerMedia(toMediaEntity(card.answer().mediaAsset()));
        return entity;
    }

    private String newUserDeckId() {
        return "user-" + UUID.randomUUID();
    }

    private String readyUserDeckId(Long chatId, String readyDeckId) {
        return "ready-" + chatId + "-" + readyDeckId;
    }

    private MediaAssetEntity toMediaEntity(MediaAsset mediaAsset) {
        if (mediaAsset == null) {
            return null;
        }
        return mediaAssetRepository.getReferenceById(mediaAsset.id());
    }

    private Deck toDeck(DeckEntity entity) {
        return new Deck(entity.getId(), entity.getTitle(), entity.getCards().stream()
                .sorted((left, right) -> Integer.compare(left.getOrderIndex(), right.getOrderIndex()))
                .map(this::toStudyCard)
                .toList(), entity.getSource());
    }

    private StudyCard toStudyCard(CardEntity entity) {
        return new StudyCard(entity.getId(),
                toCardSide(entity.getQuestionType(), entity.getQuestionText(), entity.getQuestionMedia()),
                toCardSide(entity.getAnswerType(), entity.getAnswerText(), entity.getAnswerMedia()));
    }

    private CardSide toCardSide(CardSideType type, String text, MediaAssetEntity media) {
        if (type == CardSideType.IMAGE) {
            return CardSide.image(toMediaAsset(media));
        }
        return CardSide.text(text);
    }

    private MediaAsset toMediaAsset(MediaAssetEntity entity) {
        if (entity == null) {
            return null;
        }
        return new MediaAsset(
                entity.getId(),
                entity.getStorageKey(),
                entity.getTelegramFileId(),
                entity.getTelegramFileUniqueId(),
                entity.getMimeType(),
                entity.getFileSize(),
                entity.getWidth(),
                entity.getHeight()
        );
    }

    private ParsedDeck parseDeck(String payload, String fallbackTitle) {
        String title = normalizeTitle(fallbackTitle);
        List<StudyCard> cards = new ArrayList<>();

        for (String rawLine : payload.split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            if (line.toLowerCase().startsWith("название:")) {
                String customTitle = line.substring("название:".length()).trim();
                if (!customTitle.isBlank()) {
                    title = customTitle;
                }
                continue;
            }

            String[] parts = line.split("\\s*::\\s*", 2);
            if (parts.length == 2) {
                Optional<CardSide> question = parseSide(parts[0].trim());
                Optional<CardSide> answer = parseSide(parts[1].trim());
                if (question.isPresent() && answer.isPresent()) {
                    cards.add(new StudyCard(null, question.get(), answer.get()));
                }
            }
        }
        return new ParsedDeck(title, List.copyOf(cards));
    }

    private Optional<CardSide> parseSide(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        if (value.startsWith("[image:") && value.endsWith("]")) {
            String idValue = value.substring("[image:".length(), value.length() - 1);
            try {
                return mediaAssetRepository.findById(Long.parseLong(idValue))
                        .map(this::toMediaAsset)
                        .map(CardSide::image);
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.of(CardSide.text(value));
    }

    private String normalizeTitle(String title) {
        return title == null ? "" : title.trim();
    }

    private FsrsCardState toFsrsState(FsrsCardStateEntity entity) {
        return new FsrsCardState(
                entity.getDifficulty(),
                entity.getStability(),
                entity.getRepetitions(),
                entity.getLapses(),
                entity.getLastReviewedAt(),
                entity.getDueAt()
        );
    }

    private void saveFsrsState(Long chatId, Long cardId, FsrsCardState state) {
        FsrsCardStateEntity entity = fsrsCardStateRepository.findByChatIdAndCardId(chatId, cardId)
                .orElseGet(FsrsCardStateEntity::new);
        entity.setChatId(chatId);
        entity.setCardId(cardId);
        entity.setDifficulty(state.difficulty());
        entity.setStability(state.stability());
        entity.setRepetitions(state.repetitions());
        entity.setLapses(state.lapses());
        entity.setLastReviewedAt(state.lastReviewedAt());
        entity.setDueAt(state.dueAt());
        fsrsCardStateRepository.save(entity);
    }

    private void saveReviewLog(Long chatId, Long cardId, FsrsRating rating, Instant reviewedAt, FsrsReviewResult result) {
        ReviewLogEntity entity = new ReviewLogEntity();
        entity.setChatId(chatId);
        entity.setCardId(cardId);
        entity.setRating(rating.name());
        entity.setReviewedAt(reviewedAt);
        entity.setScheduledDays(result.scheduledDays());
        entity.setDifficulty(result.state().difficulty());
        entity.setStability(result.state().stability());
        reviewLogRepository.save(entity);
    }

    private record StudySessionKey(Long chatId, String deckId) {
    }

    private record StudyItem(String deckId, String deckTitle, StudyCard card) {
    }

    private record ParsedDeck(String title, List<StudyCard> cards) {
    }

    private static final class StudySession {
        private final String studyTitle;
        private final Queue<StudyItem> queue;
        private final Set<Long> rememberedCardIds = new HashSet<>();
        private final int totalCards;
        private StudyItem currentItem;
        private Instant lastTouchedAt;

        private StudySession(String studyTitle, Queue<StudyItem> queue, StudyItem currentItem, int totalCards, Instant lastTouchedAt) {
            this.studyTitle = studyTitle;
            this.queue = queue;
            this.currentItem = currentItem;
            this.totalCards = totalCards;
            this.lastTouchedAt = lastTouchedAt;
        }

        private static StudySession start(String studyTitle, List<StudyItem> items, Instant now) {
            Queue<StudyItem> queue = new ArrayDeque<>(items);
            return new StudySession(studyTitle, queue, queue.poll(), items.size(), now);
        }

        private boolean apply(FsrsRating rating, Instant now) {
            touch(now);
            if (currentItem == null) {
                return true;
            }
            if (rating == FsrsRating.GOOD) {
                rememberedCardIds.add(currentItem.card().id());
            } else {
                queue.add(currentItem);
            }
            currentItem = queue.poll();
            return currentItem == null;
        }

        private StudyItem currentItem() {
            return currentItem;
        }

        private String studyTitle() {
            return studyTitle;
        }

        private int rememberedCount() {
            return rememberedCardIds.size();
        }

        private int totalCards() {
            return totalCards;
        }

        private int remainingCount() {
            return queue.size() + (currentItem == null ? 0 : 1);
        }

        private void touch(Instant now) {
            lastTouchedAt = now;
        }

        private boolean isActive(Instant now) {
            return Duration.between(lastTouchedAt, now).compareTo(ACTIVE_STUDY_TIMEOUT) <= 0;
        }
    }

    private enum UploadMode {
        TEXT,
        IMAGE
    }

    private enum DraftStep {
        TITLE,
        TEXT_CARDS,
        IMAGE_QUESTION,
        IMAGE_ANSWER,
        IMAGE_NEXT
    }

    private static final class DeckDraft {
        private final UploadMode mode;
        private final List<StudyCard> cards = new ArrayList<>();
        private String title;
        private DraftStep step = DraftStep.TITLE;
        private CardSide pendingQuestion;

        private DeckDraft(UploadMode mode) {
            this.mode = mode;
        }

        private static DeckDraft start(UploadMode mode) {
            return new DeckDraft(mode);
        }

        private UploadMode mode() {
            return mode;
        }

        private List<StudyCard> cards() {
            return cards;
        }

        private String title() {
            return title;
        }

        private void setTitle(String title) {
            this.title = title;
        }

        private DraftStep step() {
            return step;
        }

        private void setStep(DraftStep step) {
            this.step = step;
        }

        private CardSide pendingQuestion() {
            return pendingQuestion;
        }

        private void setPendingQuestion(CardSide pendingQuestion) {
            this.pendingQuestion = pendingQuestion;
        }
    }

    public record ImportDeckResult(boolean success, String message, Deck deck) {
        public static ImportDeckResult success(Deck deck) {
            return new ImportDeckResult(true, "", deck);
        }

        public static ImportDeckResult failure(String message) {
            return new ImportDeckResult(false, message, null);
        }
    }

    public record AddImageCardResult(boolean success, String message) {
        public static AddImageCardResult success(String message) {
            return new AddImageCardResult(true, message);
        }

        public static AddImageCardResult failure(String message) {
            return new AddImageCardResult(false, message);
        }
    }

    public record AddReadyDeckResult(AddReadyDeckStatus status, Deck deck) {
    }

    public enum AddReadyDeckStatus {
        ADDED,
        ALREADY_EXISTS,
        NOT_FOUND
    }

    public record CurrentCard(
            StudyCard card,
            String deckId,
            String deckTitle,
            String studyTitle,
            int rememberedCards,
            int totalCards,
            int remainingCards,
            FsrsCardState fsrsState
    ) {
    }

    public record StudyStartResult(StudyStartStatus status, String studyId, String title, int dueCards) {
        public static StudyStartResult started(String studyId, String title, int dueCards) {
            return new StudyStartResult(StudyStartStatus.STARTED, studyId, title, dueCards);
        }

        public static StudyStartResult noCardsDue(String title) {
            return new StudyStartResult(StudyStartStatus.NO_CARDS_DUE, null, title, 0);
        }

        public static StudyStartResult notFound() {
            return new StudyStartResult(StudyStartStatus.NOT_FOUND, null, "", 0);
        }
    }

    public enum StudyStartStatus {
        STARTED,
        NO_CARDS_DUE,
        NOT_FOUND
    }

    public record StudyProgress(
            boolean finished,
            int rememberedCards,
            int totalCards,
            int remainingCards,
            FsrsReviewResult reviewResult
    ) {
        public static StudyProgress empty() {
            return new StudyProgress(true, 0, 0, 0, null);
        }

        public static StudyProgress finished(int totalCards, FsrsReviewResult reviewResult) {
            return new StudyProgress(true, totalCards, totalCards, 0, reviewResult);
        }
    }
}
