package ru.hse.coursework.persistence.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hse.coursework.model.DeckSource;
import ru.hse.coursework.persistence.entity.DeckEntity;

import java.util.List;
import java.util.Optional;

public interface DeckRepository extends JpaRepository<DeckEntity, String> {
    @EntityGraph(attributePaths = {"cards", "cards.questionMedia", "cards.answerMedia"})
    List<DeckEntity> findBySourceAndOwnerChatIdIsNullOrderByTitle(DeckSource source);

    @EntityGraph(attributePaths = {"cards", "cards.questionMedia", "cards.answerMedia"})
    List<DeckEntity> findByOwnerChatIdOrderByCreatedAtAsc(Long ownerChatId);

    @EntityGraph(attributePaths = {"cards", "cards.questionMedia", "cards.answerMedia"})
    Optional<DeckEntity> findWithCardsById(String id);

    Optional<DeckEntity> findByOwnerChatIdAndReadySourceId(Long ownerChatId, String readySourceId);
}
