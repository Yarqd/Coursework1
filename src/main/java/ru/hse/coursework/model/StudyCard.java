package ru.hse.coursework.model;

public record StudyCard(Long id, CardSide question, CardSide answer) {
    public static StudyCard text(String question, String answer) {
        return new StudyCard(null, CardSide.text(question), CardSide.text(answer));
    }
}
