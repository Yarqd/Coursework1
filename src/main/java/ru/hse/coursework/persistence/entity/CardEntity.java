package ru.hse.coursework.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import ru.hse.coursework.model.CardSideType;

import java.time.Instant;

@Entity
@Table(name = "cards")
public class CardEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deck_id", nullable = false)
    private DeckEntity deck;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    private CardSideType questionType;

    @Column(name = "question_text")
    private String questionText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_media_id")
    private MediaAssetEntity questionMedia;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_type", nullable = false)
    private CardSideType answerType;

    @Column(name = "answer_text")
    private String answerText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_media_id")
    private MediaAssetEntity answerMedia;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public DeckEntity getDeck() {
        return deck;
    }

    public void setDeck(DeckEntity deck) {
        this.deck = deck;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public CardSideType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(CardSideType questionType) {
        this.questionType = questionType;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public MediaAssetEntity getQuestionMedia() {
        return questionMedia;
    }

    public void setQuestionMedia(MediaAssetEntity questionMedia) {
        this.questionMedia = questionMedia;
    }

    public CardSideType getAnswerType() {
        return answerType;
    }

    public void setAnswerType(CardSideType answerType) {
        this.answerType = answerType;
    }

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
    }

    public MediaAssetEntity getAnswerMedia() {
        return answerMedia;
    }

    public void setAnswerMedia(MediaAssetEntity answerMedia) {
        this.answerMedia = answerMedia;
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
