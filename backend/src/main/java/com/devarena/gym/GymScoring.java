package com.devarena.gym;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic scoring for Brain Gym. Every score is 0–100, and every rule is shown to players.
 *
 * <ul>
 *   <li><b>Estimation</b>: 70% accuracy, 100 − 40 × |log10(guess / truth)| (one order of magnitude off
 *       scores 60), plus 30% interval quality: if the low–high range contains the truth,
 *       100 − 35 × log10(high / low), otherwise 0. "Correct" means within 2× of the truth.</li>
 *   <li><b>Incident</b>: 70 for the right root cause + 30 for the right first action, minus the cost
 *       of every clue revealed (only when something was right). "Correct" = right root cause.</li>
 *   <li><b>Review</b>: F1 of flagged lines. A bug counts as found when any of its lines is flagged
 *       (recall); flags outside any bug are false alarms (precision). "Correct" = F1 ≥ 0.67.</li>
 *   <li><b>Trade-off</b>: 75% × the chosen option's authored score (defensible options earn partial
 *       credit) + 25 for naming the main risk. "Correct" = the best option.</li>
 *   <li><b>Calibration</b>: Brier score of stated confidence against correctness, mapped to 0–100 where
 *       always answering "50%" scores 0 and perfect, confident accuracy scores 100.</li>
 * </ul>
 */
public final class GymScoring {
    private GymScoring() {}

    public record Result(int score, boolean correct) {}

    public static Result estimation(double guess, double low, double high, double truth) {
        if (!(guess > 0 && low > 0 && high > 0 && truth > 0)) throw new IllegalArgumentException("Values must be positive");
        if (low > guess || guess > high) throw new IllegalArgumentException("Guess must lie inside the range");
        double logErr = Math.abs(Math.log10(guess / truth));
        double accuracy = clamp(100 - 40 * logErr);
        boolean contains = low <= truth && truth <= high;
        double widthOrders = Math.log10(high / low);
        double interval = contains ? clamp(100 - 35 * widthOrders) : 0;
        int score = (int) Math.round(0.7 * accuracy + 0.3 * interval);
        return new Result(score, logErr <= Math.log10(2) + 1e-9);
    }

    public static Result incident(boolean rootCauseRight, boolean actionRight, int clueCost) {
        int base = (rootCauseRight ? 70 : 0) + (actionRight ? 30 : 0);
        int score = base == 0 ? 0 : Math.max(0, base - Math.max(0, clueCost));
        return new Result(score, rootCauseRight);
    }

    /** @param bugs each bug's line numbers */
    public static Result review(Collection<Integer> flagged, List<List<Integer>> bugs) {
        Set<Integer> flags = new HashSet<>(flagged);
        if (flags.isEmpty() || bugs.isEmpty()) return new Result(0, false);
        Set<Integer> bugLines = new HashSet<>();
        bugs.forEach(bugLines::addAll);
        long found = bugs.stream().filter(b -> b.stream().anyMatch(flags::contains)).count();
        long relevant = flags.stream().filter(bugLines::contains).count();
        double recall = found / (double) bugs.size();
        double precision = relevant / (double) flags.size();
        double f1 = (precision + recall) == 0 ? 0 : 2 * precision * recall / (precision + recall);
        return new Result((int) Math.round(100 * f1), f1 >= 0.67);
    }

    public static Result tradeoff(int optionScore, boolean riskRight, boolean bestOption) {
        int score = (int) Math.round(0.75 * Math.max(0, Math.min(100, optionScore)) + (riskRight ? 25 : 0));
        return new Result(score, bestOption);
    }

    /** Mean Brier score for (confidence%, correct) pairs, mapped so 0.25 (coin-flip) → 0 and 0 → 100. */
    public static Integer calibration(List<int[]> confidenceAndCorrect) {
        if (confidenceAndCorrect.isEmpty()) return null;
        double sum = 0;
        for (int[] cc : confidenceAndCorrect) {
            double p = cc[0] / 100.0;
            double o = cc[1];
            sum += (p - o) * (p - o);
        }
        double brier = sum / confidenceAndCorrect.size();
        return (int) Math.round(clamp(100 * (1 - brier / 0.25)));
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }
}
