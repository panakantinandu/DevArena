package com.devarena.questions;

import com.devarena.auth.AppUserPrincipal;
import com.devarena.common.Category;
import com.devarena.common.Difficulty;
import com.devarena.common.PageResponse;
import com.devarena.questions.QuestionDtos.AnswerRequest;
import com.devarena.questions.QuestionDtos.AnswerResult;
import com.devarena.questions.QuestionDtos.AttemptView;
import com.devarena.questions.QuestionDtos.QuestionFilter;
import com.devarena.questions.QuestionDtos.QuestionSummary;
import com.devarena.questions.QuestionDtos.QuestionView;
import com.devarena.questions.QuestionDtos.TopicSummary;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class QuestionController {
    private final QuestionService questions;

    public QuestionController(QuestionService questions) {
        this.questions = questions;
    }

    @GetMapping("/topics")
    public List<TopicSummary> topics(@AuthenticationPrincipal AppUserPrincipal me) {
        return questions.topics(me.id());
    }

    @GetMapping("/questions")
    public PageResponse<QuestionSummary> list(@AuthenticationPrincipal AppUserPrincipal me,
                                              @RequestParam(required = false) Category category,
                                              @RequestParam(required = false) String topic,
                                              @RequestParam(required = false) Difficulty difficulty,
                                              @RequestParam(required = false) String search,
                                              @RequestParam(defaultValue = "false") boolean bookmarked,
                                              @RequestParam(defaultValue = "false") boolean mistakes,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        return questions.list(me.id(), new QuestionFilter(category, topic, difficulty, search, bookmarked, mistakes,
                Math.max(page, 0), safeSize));
    }

    @GetMapping("/questions/{id}")
    public QuestionView view(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
        return questions.view(me.id(), id);
    }

    @PostMapping("/questions/{id}/answer")
    public AnswerResult answer(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id,
                               @Valid @RequestBody AnswerRequest req) {
        return questions.answerPractice(me.id(), id, req.optionId());
    }

    @PostMapping("/questions/{id}/bookmark")
    public Map<String, Boolean> bookmark(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
        return Map.of("bookmarked", questions.toggleBookmark(me.id(), id));
    }

    @GetMapping("/attempts")
    public PageResponse<AttemptView> attempts(@AuthenticationPrincipal AppUserPrincipal me,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return questions.history(me.id(), Math.max(page, 0), Math.min(Math.max(size, 1), 50));
    }
}
