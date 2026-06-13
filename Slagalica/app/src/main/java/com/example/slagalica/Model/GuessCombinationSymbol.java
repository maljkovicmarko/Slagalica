package com.example.slagalica.Model;

public class GuessCombinationSymbol {
    private String name;
    private int drawableResId;

    public GuessCombinationSymbol() {
    }

    public GuessCombinationSymbol(String name, int drawableResId) {
        this.name = name;
        this.drawableResId = drawableResId;
    }

    public String getName() {
        return name;
    }

    public int getDrawableResId() {
        return drawableResId;
    }
}