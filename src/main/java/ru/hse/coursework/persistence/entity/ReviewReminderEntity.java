package ru.hse.coursework.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "review_reminders")
public class ReviewReminderEntity {
    @Id
    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "due_card_signature", nullable = false)
    private String dueCardSignature;

    @Column(name = "reminded_at")
    private Instant remindedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public String getDueCardSignature() {
        return dueCardSignature;
    }

    public void setDueCardSignature(String dueCardSignature) {
        this.dueCardSignature = dueCardSignature;
    }

    public Instant getRemindedAt() {
        return remindedAt;
    }

    public void setRemindedAt(Instant remindedAt) {
        this.remindedAt = remindedAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
