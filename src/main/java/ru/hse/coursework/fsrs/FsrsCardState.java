package ru.hse.coursework.fsrs;

import java.time.Instant;

public record FsrsCardState(
        double difficulty,
        double stability,
        int repetitions,
        int lapses,
        Instant lastReviewedAt,
        Instant dueAt
) {
    public static FsrsCardState newCard(Instant now) {
        return new FsrsCardState(0.0, 0.0, 0, 0, null, now);
    }
}
