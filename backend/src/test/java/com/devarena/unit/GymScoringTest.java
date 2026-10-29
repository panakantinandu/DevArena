package com.devarena.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devarena.gym.GymScoring;
import java.util.List;
import org.junit.jupiter.api.Test;

class GymScoringTest {

    @Test
    void estimationRewardsAccuracyAndTightCorrectIntervals() {
        // Exact guess with a 2× range around it.
        var exact = GymScoring.estimation(1000, 700, 1400, 1000);
        // One order of magnitude off: accuracy 60, range misses the truth.
        var tenX = GymScoring.estimation(10_000, 7_000, 14_000, 1000);
        // Good guess, but a hugely wide "safe" range.
        var wide = GymScoring.estimation(1000, 1, 1_000_000, 1000);
        assertThat(exact.score()).isGreaterThan(wide.score()).isGreaterThan(tenX.score());
        assertThat(exact.correct()).isTrue();
        assertThat(tenX.correct()).isFalse();
        assertThat(tenX.score()).isEqualTo((int) Math.round(0.7 * 60));
        // Within 2× counts as correct, either direction.
        assertThat(GymScoring.estimation(1999, 1000, 3000, 1000).correct()).isTrue();
        assertThat(GymScoring.estimation(499, 100, 1000, 1000).correct()).isFalse();
    }

    @Test
    void estimationRejectsInvalidInput() {
        assertThatThrownBy(() -> GymScoring.estimation(0, 0, 10, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GymScoring.estimation(50, 1, 10, 5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void incidentPenalisesCluesOnlyWhenSomethingIsRight() {
        assertThat(GymScoring.incident(true, true, 0).score()).isEqualTo(100);
        assertThat(GymScoring.incident(true, true, 35).score()).isEqualTo(65);
        assertThat(GymScoring.incident(true, false, 10).score()).isEqualTo(60);
        assertThat(GymScoring.incident(false, false, 40).score()).isZero();
        assertThat(GymScoring.incident(false, true, 10).correct()).isFalse();
    }

    @Test
    void reviewIsF1OverBugsAndFalseAlarms() {
        List<List<Integer>> bugs = List.of(List.of(3), List.of(7, 9, 10));
        assertThat(GymScoring.review(List.of(3, 9), bugs).score()).isEqualTo(100);
        // Found one of two bugs with no false alarms: P=1, R=0.5, F1≈0.67.
        assertThat(GymScoring.review(List.of(3), bugs).score()).isEqualTo(67);
        // Flagging every line finds everything but precision suffers.
        var shotgun = GymScoring.review(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12), bugs);
        assertThat(shotgun.score()).isLessThan(70);
        assertThat(GymScoring.review(List.of(), bugs).score()).isZero();
    }

    @Test
    void tradeoffGivesPartialCreditAndRiskBonus() {
        assertThat(GymScoring.tradeoff(100, true, true).score()).isEqualTo(100);
        assertThat(GymScoring.tradeoff(60, true, false).score()).isEqualTo(70);
        assertThat(GymScoring.tradeoff(60, false, false).correct()).isFalse();
    }

    @Test
    void calibrationRewardsHonestConfidence() {
        // Always 99% sure and always right: near perfect.
        assertThat(GymScoring.calibration(List.of(new int[]{99, 1}, new int[]{99, 1}))).isGreaterThan(95);
        // Coin-flip confidence scores 0.
        assertThat(GymScoring.calibration(List.of(new int[]{50, 1}, new int[]{50, 0}))).isZero();
        // Overconfident and wrong is clamped at 0.
        assertThat(GymScoring.calibration(List.of(new int[]{95, 0}, new int[]{95, 0}))).isZero();
        // Honest 70% on a 70% hit rate beats overconfident 99% on the same record.
        List<int[]> honest = List.of(new int[]{70, 1}, new int[]{70, 1}, new int[]{70, 1}, new int[]{70, 0}, new int[]{70, 1},
                new int[]{70, 1}, new int[]{70, 0}, new int[]{70, 1}, new int[]{70, 0}, new int[]{70, 1});
        List<int[]> cocky = honest.stream().map(p -> new int[]{99, p[1]}).toList();
        assertThat(GymScoring.calibration(honest)).isGreaterThan(GymScoring.calibration(cocky));
        assertThat(GymScoring.calibration(List.of())).isNull();
    }
}
