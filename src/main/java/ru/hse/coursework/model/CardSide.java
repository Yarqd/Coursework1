package ru.hse.coursework.model;

public record CardSide(CardSideType type, String text, MediaAsset mediaAsset) {
    public static CardSide text(String text) {
        return new CardSide(CardSideType.TEXT, text, null);
    }

    public static CardSide image(MediaAsset mediaAsset) {
        return new CardSide(CardSideType.IMAGE, null, mediaAsset);
    }

    public String displayText() {
        if (type == CardSideType.TEXT) {
            return text;
        }
        return "[image:" + mediaAsset.id() + "]";
    }
}
