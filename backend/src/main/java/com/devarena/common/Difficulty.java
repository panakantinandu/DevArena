package com.devarena.common;

public enum Difficulty {
    EASY(1.0), MEDIUM(1.5), HARD(2.0);

    private final double weight;

    Difficulty(double weight) { this.weight = weight; }

    /** Weight used by the mastery model: harder questions count more. */
    public double weight() { return weight; }
}
