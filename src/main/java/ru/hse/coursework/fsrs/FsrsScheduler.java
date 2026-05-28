package ru.hse.coursework.fsrs;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class FsrsScheduler {
    private static final double[] DEFAULT_PARAMETERS = {
            0.4072, 1.1829, 3.1262, 15.4722, 7.2102, 0.5316, 1.0651, 0.0234, 1.616,
            0.1544, 1.0824, 1.9813, 0.0953, 0.2975, 2.2042, 0.2407, 2.9466, 0.5034, 0.6567
    };

    public FsrsReviewResult review(FsrsCardState previousState, FsrsRating rating, Instant now) {
        FsrsCardState state = previousState == null ? FsrsCardState.newCard(now) : previousState;
        double difficulty = state.repetitions() == 0
                ? initDifficulty(rating)
                : nextDifficulty(state.difficulty(), rating);
        double stability = state.repetitions() == 0
                ? initStability(rating)
                : nextStability(state, difficulty, rating, now);

        int scheduledDays = Math.max(1, (int) Math.round(stability));
        FsrsCardState nextState = new FsrsCardState(
                clamp(difficulty, 1.0, 10.0),
                Math.max(0.1, stability),
                state.repetitions() + 1,
                state.lapses() + (rating == FsrsRating.AGAIN ? 1 : 0),
                now,
                now.plus(Duration.ofDays(scheduledDays))
        );
        return new FsrsReviewResult(nextState, scheduledDays);
    }

    private double initStability(FsrsRating rating) {
        return DEFAULT_PARAMETERS[rating.grade() - 1];
    }

    private double initDifficulty(FsrsRating rating) {
        double value = DEFAULT_PARAMETERS[4] - Math.exp(DEFAULT_PARAMETERS[5] * (rating.grade() - 1)) + 1.0;
        return clamp(value, 1.0, 10.0);
    }

    private double nextDifficulty(double currentDifficulty, FsrsRating rating) {
        double deltaDifficulty = -DEFAULT_PARAMETERS[6] * (rating.grade() - 3);
        double dampedDelta = deltaDifficulty * (10.0 - currentDifficulty) / 9.0;
        double nextDifficulty = currentDifficulty + dampedDelta;
        double easyBaseline = initDifficulty(FsrsRating.GOOD) - DEFAULT_PARAMETERS[6] * (4 - 3);
        double reverted = DEFAULT_PARAMETERS[7] * easyBaseline + (1 - DEFAULT_PARAMETERS[7]) * nextDifficulty;
        return clamp(reverted, 1.0, 10.0);
    }

    private double nextStability(FsrsCardState state, double difficulty, FsrsRating rating, Instant now) {
        double retrievability = retrievability(state, now);
        if (rating == FsrsRating.AGAIN) {
            return forgettingStability(difficulty, state.stability(), retrievability);
        }

        double hardPenalty = rating == FsrsRating.HARD ? DEFAULT_PARAMETERS[15] : 1.0;
        double growth = Math.exp(DEFAULT_PARAMETERS[8])
                * (11.0 - difficulty)
                * Math.pow(Math.max(state.stability(), 0.1), -DEFAULT_PARAMETERS[9])
                * (Math.exp((1.0 - retrievability) * DEFAULT_PARAMETERS[10]) - 1.0)
                * hardPenalty;
        double next = state.stability() * (1.0 + Math.max(growth, 0.0));
        if (rating == FsrsRating.HARD) {
            next = Math.min(next, state.stability() + 1.0);
        }
        return Math.max(0.1, next);
    }

    private double forgettingStability(double difficulty, double stability, double retrievability) {
        double next = DEFAULT_PARAMETERS[11]
                * Math.pow(difficulty, -DEFAULT_PARAMETERS[12])
                * (Math.pow(stability + 1.0, DEFAULT_PARAMETERS[13]) - 1.0)
                * Math.exp(DEFAULT_PARAMETERS[14] * (1.0 - retrievability));
        return Math.min(Math.max(next, 0.1), Math.max(stability, 0.1));
    }

    private double retrievability(FsrsCardState state, Instant now) {
        if (state.lastReviewedAt() == null || state.stability() <= 0.0) {
            return 1.0;
        }

        double elapsedDays = Math.max(0.0, Duration.between(state.lastReviewedAt(), now).toHours() / 24.0);
        return Math.pow(0.9, elapsedDays / state.stability());
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
