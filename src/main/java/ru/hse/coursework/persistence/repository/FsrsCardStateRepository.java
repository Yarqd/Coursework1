package ru.hse.coursework.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.hse.coursework.persistence.entity.FsrsCardStateEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FsrsCardStateRepository extends JpaRepository<FsrsCardStateEntity, Long> {
    Optional<FsrsCardStateEntity> findByChatIdAndCardId(Long chatId, Long cardId);

    List<FsrsCardStateEntity> findByDueAtLessThanEqual(Instant dueAt);

    List<FsrsCardStateEntity> findByChatIdAndDueAtLessThanEqual(Long chatId, Instant dueAt);

    void deleteByChatIdAndCardIdIn(Long chatId, Iterable<Long> cardIds);
}
