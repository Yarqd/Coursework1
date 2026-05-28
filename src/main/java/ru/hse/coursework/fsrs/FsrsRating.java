package ru.hse.coursework.fsrs;

public enum FsrsRating {
    AGAIN(1),
    HARD(2),
    GOOD(3);

    private final int grade;

    FsrsRating(int grade) {
        this.grade = grade;
    }

    public int grade() {
        return grade;
    }

    public static FsrsRating fromCallbackGrade(String grade) {
        return switch (grade) {
            case "good" -> GOOD;
            case "hard" -> HARD;
            case "bad" -> AGAIN;
            default -> throw new IllegalArgumentException("Unsupported grade: " + grade);
        };
    }
}
