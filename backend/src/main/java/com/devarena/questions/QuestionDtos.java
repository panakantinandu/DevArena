package com.devarena.questions;

import com.devarena.common.Category;
import com.devarena.common.Difficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class QuestionDtos {
    private QuestionDtos() {}

    public record TopicRef(UUID id, String slug, String name, Category category) {
        public static TopicRef of(Topic t) {
            return new TopicRef(t.getId(), t.getSlug(), t.getName(), t.getCategory());
        }
    }

    public record TopicSummary(UUID id, String slug, String name, Category category, String categoryLabel,
                               String description, int questionCount, Double mastery) {}

    public record QuestionSummary(UUID id, String slug, TopicRef topic, Difficulty difficulty, String prompt,
                                  boolean bookmarked, Boolean lastAttemptCorrect, Instant lastAttemptAt) {}

    /** A question as shown before answering: options carry no correctness flag. */
    public record OptionView(UUID id, String body) {}

    public record QuestionView(UUID id, String slug, TopicRef topic, Difficulty difficulty, String prompt,
                               String codeSnippet, List<OptionView> options, boolean bookmarked) {}

    public record AnswerRequest(@NotNull UUID optionId) {}

    public record AnswerResult(boolean correct, UUID correctOptionId, String explanation, int xpAwarded) {}

    public record AttemptView(UUID questionId, String prompt, String topicName, Difficulty difficulty, boolean correct,
                              String context, Instant at) {}

    // ---------------------------------------------------------------- admin

    public record AdminOption(@NotBlank @Size(max = 600) String body, boolean correct) {}

    public record AdminQuestionRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[a-z0-9-]+$", message = "Lowercase letters, digits and dashes")
            String slug,
            @NotBlank String topicSlug,
            @NotNull Difficulty difficulty,
            @NotBlank @Size(max = 4000) String prompt,
            @Size(max = 4000) String codeSnippet,
            @NotBlank @Size(max = 4000) String explanation,
            @NotNull Question.Status status,
            @NotNull @Size(min = 2, max = 6) @Valid List<AdminOption> options) {}

    public record AdminQuestionView(UUID id, String slug, String topicSlug, Difficulty difficulty, String prompt,
                                    String codeSnippet, String explanation, Question.Status status,
                                    List<AdminOption> options, Instant updatedAt) {}

    public record QuestionFilter(Category category, String topic, Difficulty difficulty, String search,
                                 boolean bookmarkedOnly, boolean mistakesOnly,
                                 @Min(0) int page, @Min(1) @Max(50) int size) {}
}
