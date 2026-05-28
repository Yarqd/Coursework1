package ru.hse.coursework.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import ru.hse.coursework.model.DeckSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "decks")
public class DeckEntity {
    @Id
    private String id;

    @Column(name = "owner_chat_id")
    private Long ownerChatId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeckSource source;

    @Column(name = "ready_source_id")
    private String readySourceId;

    @OneToMany(mappedBy = "deck", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CardEntity> cards = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getOwnerChatId() {
        return ownerChatId;
    }

    public void setOwnerChatId(Long ownerChatId) {
        this.ownerChatId = ownerChatId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public DeckSource getSource() {
        return source;
    }

    public void setSource(DeckSource source) {
        this.source = source;
    }

    public String getReadySourceId() {
        return readySourceId;
    }

    public void setReadySourceId(String readySourceId) {
        this.readySourceId = readySourceId;
    }

    public List<CardEntity> getCards() {
        return cards;
    }

    public void replaceCards(List<CardEntity> newCards) {
        cards.clear();
        for (int i = 0; i < newCards.size(); i++) {
            CardEntity card = newCards.get(i);
            card.setDeck(this);
            card.setOrderIndex(i);
            cards.add(card);
        }
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
