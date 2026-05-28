package ru.hse.coursework.model;

import java.util.List;

public record Deck(String id, String title, List<StudyCard> cards, DeckSource source) {
    public int size() {
        return cards.size();
    }

    public Deck copyForUser(String newId) {
        return new Deck(newId, title, List.copyOf(cards), DeckSource.USER);
    }
}
