package ru.hse.coursework.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hse.coursework.persistence.entity.FsrsCardStateEntity;
import ru.hse.coursework.persistence.entity.ReviewReminderEntity;
import ru.hse.coursework.persistence.entity.UserEntity;
import ru.hse.coursework.persistence.repository.FsrsCardStateRepository;
import ru.hse.coursework.persistence.repository.ReviewReminderRepository;
import ru.hse.coursework.persistence.repository.UserRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ReviewReminderService {
    private final FsrsCardStateRepository fsrsCardStateRepository;
    private final ReviewReminderRepository reviewReminderRepository;
    private final UserRepository userRepository;

    public ReviewReminderService(
            FsrsCardStateRepository fsrsCardStateRepository,
            ReviewReminderRepository reviewReminderRepository,
            UserRepository userRepository
    ) {
        this.fsrsCardStateRepository = fsrsCardStateRepository;
        this.reviewReminderRepository = reviewReminderRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ReminderCandidate> pendingReminderCandidates(Instant now) {
        Map<Long, List<FsrsCardStateEntity>> dueStatesByChat = fsrsCardStateRepository.findByDueAtLessThanEqual(now)
                .stream()
                .filter(state -> state.getRepetitions() != null && state.getRepetitions() > 0)
                .collect(Collectors.groupingBy(
                        FsrsCardStateEntity::getChatId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return dueStatesByChat.entrySet().stream()
                .map(entry -> toCandidate(entry.getKey(), entry.getValue()))
                .flatMap(Optional::stream)
                .toList();
    }

    @Transactional
    public void markReminderSent(Long chatId, String dueCardSignature, Instant now) {
        if (chatId == null || dueCardSignature == null || dueCardSignature.isBlank()) {
            return;
        }
        ReviewReminderEntity reminder = reviewReminderRepository.findById(chatId)
                .orElseGet(ReviewReminderEntity::new);
        reminder.setChatId(chatId);
        reminder.setDueCardSignature(dueCardSignature);
        reminder.setRemindedAt(now);
        reminder.setAcknowledgedAt(null);
        reviewReminderRepository.save(reminder);
    }

    @Transactional
    public void acknowledgeCurrentDue(Long chatId, Instant now) {
        if (chatId == null) {
            return;
        }
        String signature = dueSignatureForChat(chatId, now);
        if (signature.isBlank()) {
            reviewReminderRepository.deleteById(chatId);
            return;
        }

        ReviewReminderEntity reminder = reviewReminderRepository.findById(chatId)
                .orElseGet(ReviewReminderEntity::new);
        reminder.setChatId(chatId);
        reminder.setDueCardSignature(signature);
        reminder.setAcknowledgedAt(now);
        reviewReminderRepository.save(reminder);
    }

    private Optional<ReminderCandidate> toCandidate(Long chatId, List<FsrsCardStateEntity> dueStates) {
        String signature = dueSignature(dueStates);
        if (signature.isBlank()) {
            return Optional.empty();
        }

        Optional<ReviewReminderEntity> reminder = reviewReminderRepository.findById(chatId);
        if (reminder.isPresent()
                && signature.equals(reminder.get().getDueCardSignature())
                && (reminder.get().getRemindedAt() != null || reminder.get().getAcknowledgedAt() != null)) {
            return Optional.empty();
        }

        String firstName = userRepository.findByChatId(chatId)
                .map(UserEntity::getFirstName)
                .filter(name -> !name.isBlank())
                .orElse("друг");
        return Optional.of(new ReminderCandidate(chatId, firstName, signature, dueStates.size()));
    }

    private String dueSignatureForChat(Long chatId, Instant now) {
        return dueSignature(fsrsCardStateRepository.findByChatIdAndDueAtLessThanEqual(chatId, now)
                .stream()
                .filter(state -> state.getRepetitions() != null && state.getRepetitions() > 0)
                .toList());
    }

    private String dueSignature(List<FsrsCardStateEntity> dueStates) {
        return dueStates.stream()
                .map(FsrsCardStateEntity::getCardId)
                .sorted(Comparator.naturalOrder())
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    public record ReminderCandidate(Long chatId, String firstName, String dueCardSignature, int dueCards) {
    }
}
