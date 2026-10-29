package com.devarena.interviews;

import com.devarena.common.Difficulty;
import com.devarena.users.UserDtos.PublicUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Candidate-facing and interviewer-facing shapes are separate types on purpose:
 * a candidate response cannot carry guide fields because the type has none.
 */
public final class InterviewDtos {
    private InterviewDtos() {}

    public enum Role { INTERVIEWER, CANDIDATE }

    public record CreateRequest(
            @NotNull InterviewType type,
            @NotNull Difficulty difficulty,
            @Min(10) @Max(120) int durationMinutes,
            @Min(1) @Max(6) int questionCount) {}

    public record JoinRequest(@NotBlank @Pattern(regexp = "^[A-Za-z0-9]{8}$", message = "Interview codes are 8 characters")
                              String code) {}

    // ---------------------------------------------------------------- candidate-safe

    public record CodingBrief(String slug, String title, String statement, String examples, String constraints,
                              String starterCode) {}

    public record DesignBrief(UUID id, String slug, String title, String summary, List<String> functionalReqs,
                              List<String> nonFunctionalReqs) {}

    public record CandidateQuestion(UUID sessionQuestionId, int position, String title, String prompt,
                                    CodingBrief coding, DesignBrief design) {}

    public record SessionView(UUID id, String roomCode, InterviewType type, String typeLabel, Difficulty difficulty,
                              int durationMinutes, InterviewStatus status, Role yourRole, PublicUser interviewer,
                              PublicUser candidate, int currentIndex, int questionCount, Instant startedAt, Instant endsAt,
                              Instant endedAt, CandidateQuestion current, List<CandidateQuestion> asked,
                              boolean evaluated, List<UUID> online) {}

    // ---------------------------------------------------------------- interviewer-only

    public record GuideQuestion(UUID sessionQuestionId, int position, String title, String prompt,
                                List<String> expectedConcepts, String answerOutline, List<String> commonMistakes,
                                List<String> followUps, String rubricNotes, String solutionOutline,
                                List<String> designDiscussionTopics, List<String> designFollowUps,
                                String privateNote, Instant askedAt, CodingBrief coding, DesignBrief design) {}

    public record InterviewerView(SessionView session, List<GuideQuestion> questions, String generalNote,
                                  List<RubricCriterion> rubric) {}

    public record RubricCriterion(String key, String label, double weight, String description) {}

    public record NoteRequest(UUID sessionQuestionId, @NotNull @Size(max = 10_000) String body) {}

    public record EvaluationRequest(
            @Min(1) @Max(5) int technical,
            @Min(1) @Max(5) int depth,
            @Min(1) @Max(5) int problemSolving,
            @Min(1) @Max(5) int communication,
            @Min(1) @Max(5) int followUpHandling,
            @NotBlank @Size(max = 4000) String strengths,
            @NotBlank @Size(max = 4000) String improvements,
            @Size(max = 4000) String summary) {}

    // ---------------------------------------------------------------- after the interview

    public record Evaluation(int technical, int depth, int problemSolving, int communication, int followUpHandling,
                             BigDecimal overall, String band, String strengths, String improvements, String summary,
                             PublicUser evaluator, Instant createdAt) {}

    public record ReportQuestion(int position, String title, String prompt, List<String> expectedConcepts) {}

    public record Report(SessionView session, Evaluation evaluation, List<ReportQuestion> questions,
                         String finalCode, String language, int codeSubmissions) {}

    public record HistoryItem(UUID id, InterviewType type, String typeLabel, Difficulty difficulty, InterviewStatus status,
                              Role yourRole, PublicUser otherPerson, BigDecimal overall, Instant createdAt,
                              Instant endedAt) {}

    public record CodeSnapshot(UUID sessionId, String code, String language, long version) {}

    public record SubmitCodeRequest(@NotBlank @Size(max = 100_000) String code,
                                    @NotBlank @Pattern(regexp = "JAVA|PYTHON|JAVASCRIPT|TYPESCRIPT|GO|CPP|KOTLIN") String language,
                                    @Size(max = 500) String note) {}

    public record InviteRequest(@NotNull UUID userId) {}
}
