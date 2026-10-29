package com.devarena.battles;

import com.devarena.common.Category;
import com.devarena.common.Difficulty;
import com.devarena.questions.QuestionDtos.QuestionView;
import com.devarena.users.UserDtos.PublicUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BattleDtos {
    private BattleDtos() {}

    public record CreateBattleRequest(
            @NotNull Category category,
            @NotNull Difficulty difficulty,
            @Min(3) @Max(20) int questionCount,
            @Min(10) @Max(120) int secondsPerQuestion) {}

    public record JoinRequest(@NotNull @Pattern(regexp = "^[A-Za-z0-9]{6}$", message = "Room codes are 6 characters") String code) {}

    public record ReadyRequest(boolean ready) {}

    public record AnswerRequest(@Min(0) int questionIndex, @NotNull UUID optionId) {}

    public record PlayerState(PublicUser user, boolean host, boolean ready, int score, int correctCount,
                              boolean answeredCurrent, boolean online) {}

    /** A question while its round is open. Carries no correctness information. */
    public record LiveQuestion(int index, QuestionView question, Instant startedAt, Instant endsAt) {}

    public record PlayerRoundResult(UUID userId, boolean answered, boolean correct, int points) {}

    /** Revealed only after a round closes. */
    public record RoundReveal(int index, UUID correctOptionId, String explanation, List<PlayerRoundResult> results) {}

    /** Shared battle state; identical for every participant, safe to broadcast. */
    public record BattleState(UUID id, String roomCode, BattleStatus status, PublicUser host, Category category,
                              String categoryLabel, Difficulty difficulty, int questionCount, int secondsPerQuestion,
                              int currentIndex, List<PlayerState> players, LiveQuestion live, RoundReveal reveal,
                              UUID winnerId, Instant startedAt, Instant endedAt) {}

    /** State plus what only this player may see (their own pending answer). */
    public record BattleView(BattleState state, UUID yourAnswerForCurrent) {}

    public record AnswerAck(int questionIndex, boolean accepted) {}

    public record HistoryItem(UUID id, String roomCode, Category category, Difficulty difficulty, int questionCount,
                              BattleStatus status, int yourScore, int yourCorrect, int placement, int players,
                              PublicUser opponentOrWinner, boolean won, Instant endedAt) {}

    public record QuestionBreakdown(int index, String prompt, UUID correctOptionId, String correctAnswer,
                                    String explanation, List<PlayerRoundResult> results) {}

    public record BattleResults(BattleState state, List<QuestionBreakdown> questions) {}
}
