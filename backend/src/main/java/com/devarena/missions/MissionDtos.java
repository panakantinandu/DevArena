package com.devarena.missions;

import com.devarena.common.Category;
import com.devarena.common.Difficulty;
import com.devarena.questions.QuestionDtos.QuestionView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MissionDtos {
    private MissionDtos() {}

    public record MissionCard(UUID id, int slot, String kind, Category category, String categoryLabel, String topicSlug,
                              String title, Difficulty difficulty, int estimatedMinutes, int xpReward, boolean revision,
                              boolean completed, Integer correctCount, Integer totalCount, Instant completedAt) {}

    public record TodayResponse(LocalDate date, int roadmapDay, String phase, int dailyMinimum, int completedCount,
                                List<MissionCard> missions) {}

    /** Review data. Present only after the mission is completed, so answers can't be peeked. */
    public record QuestionReview(UUID questionId, UUID correctOptionId, UUID yourOptionId, boolean correct,
                                 String explanation) {}

    public record InterviewStudyItem(UUID id, String title, String prompt, String type,
                                     // Guide fields are null until the user has submitted their own answer.
                                     List<String> expectedConcepts, String answerOutline, List<String> commonMistakes,
                                     List<String> followUps) {}

    public record DesignStudyItem(UUID id, String slug, String title, String summary, List<String> functionalReqs,
                                  List<String> nonFunctionalReqs,
                                  // Null until submitted.
                                  List<String> discussionTopics, List<String> followUps) {}

    public record MissionDetail(MissionCard card, List<QuestionView> questions, List<QuestionReview> review,
                                List<InterviewStudyItem> interviewItems, DesignStudyItem design,
                                Map<String, Object> yourResponse) {}

    public record SubmitRequest(
            Map<UUID, UUID> answers,
            @Size(max = 10_000) String notes,
            @Min(1) @Max(5) Integer selfRating) {}

    public record SubmitResult(boolean firstCompletion, int xpAwarded, int correctCount, int totalCount,
                               List<String> unlockedAchievements, MissionDetail detail) {}
}
