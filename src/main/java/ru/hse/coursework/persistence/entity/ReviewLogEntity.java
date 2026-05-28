package ru.hse.coursework.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "review_logs")
public class ReviewLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(nullable = false)
    private String rating;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @Column(name = "scheduled_days", nullable = false)
    private Integer scheduledDays;

    @Column(nullable = false)
    private Double difficulty;

    @Column(nullable = false)
    private Double stability;

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public void setCardId(Long cardId) {
        this.cardId = cardId;
    }

    public void setRating(String rating) {
        this.rating = rating;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public void setScheduledDays(Integer scheduledDays) {
        this.scheduledDays = scheduledDays;
    }

    public void setDifficulty(Double difficulty) {
        this.difficulty = difficulty;
    }

    public void setStability(Double stability) {
        this.stability = stability;
    }
}
