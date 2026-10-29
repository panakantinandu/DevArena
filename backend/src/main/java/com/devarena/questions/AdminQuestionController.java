package com.devarena.questions;

import com.devarena.common.ApiException;
import com.devarena.common.Ids;
import com.devarena.questions.QuestionDtos.AdminOption;
import com.devarena.questions.QuestionDtos.AdminQuestionRequest;
import com.devarena.questions.QuestionDtos.AdminQuestionView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Question authoring. Restricted to ADMIN by SecurityConfig (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/questions")
public class AdminQuestionController {
    private final QuestionRepository questions;
    private final TopicRepository topics;
    private final JdbcTemplate jdbc;

    public AdminQuestionController(QuestionRepository questions, TopicRepository topics, JdbcTemplate jdbc) {
        this.questions = questions;
        this.topics = topics;
        this.jdbc = jdbc;
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public AdminQuestionView get(@PathVariable UUID id) {
        return toView(questions.findOneWithOptions(id).orElseThrow(() -> ApiException.notFound("Question")));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AdminQuestionView create(@Valid @RequestBody AdminQuestionRequest req) {
        validateOptions(req.options());
        if (questions.findBySlug(req.slug()).isPresent()) {
            throw ApiException.conflict("SLUG_TAKEN", "A question with that slug already exists");
        }
        Question q = new Question();
        q.setId(Ids.newId());
        apply(q, req);
        for (int i = 0; i < req.options().size(); i++) {
            QuestionOption o = new QuestionOption();
            o.setId(Ids.newId());
            o.setQuestion(q);
            o.setPosition(i);
            o.setBody(req.options().get(i).body());
            o.setCorrect(req.options().get(i).correct());
            q.getOptions().add(o);
        }
        questions.save(q);
        return toView(q);
    }

    @PutMapping("/{id}")
    @Transactional
    public AdminQuestionView update(@PathVariable UUID id, @Valid @RequestBody AdminQuestionRequest req) {
        validateOptions(req.options());
        Question q = questions.findOneWithOptions(id).orElseThrow(() -> ApiException.notFound("Question"));
        if (!q.getSlug().equals(req.slug()) && questions.findBySlug(req.slug()).isPresent()) {
            throw ApiException.conflict("SLUG_TAKEN", "A question with that slug already exists");
        }
        Integer answered = jdbc.queryForObject("select count(*) from question_attempts where question_id = ?", Integer.class, id);
        if (answered != null && answered > 0 && req.options().size() < q.getOptions().size()) {
            throw ApiException.conflict("OPTIONS_IN_USE",
                    "This question already has answers, so options can be edited but not removed. Archive it instead.");
        }
        apply(q, req);
        // Clear correctness first and flush so the one-correct-option index never sees two.
        q.getOptions().forEach(o -> o.setCorrect(false));
        questions.saveAndFlush(q);
        for (int i = 0; i < req.options().size(); i++) {
            AdminOption in = req.options().get(i);
            QuestionOption o;
            if (i < q.getOptions().size()) {
                o = q.getOptions().get(i);
            } else {
                o = new QuestionOption();
                o.setId(Ids.newId());
                o.setQuestion(q);
                o.setPosition(i);
                q.getOptions().add(o);
            }
            o.setBody(in.body());
            o.setCorrect(in.correct());
        }
        while (q.getOptions().size() > req.options().size()) q.getOptions().removeLast();
        questions.saveAndFlush(q);
        return toView(q);
    }

    private void apply(Question q, AdminQuestionRequest req) {
        Topic topic = topics.findBySlug(req.topicSlug()).orElseThrow(() -> ApiException.badRequest("UNKNOWN_TOPIC", "Unknown topic"));
        q.setSlug(req.slug());
        q.setTopic(topic);
        q.setDifficulty(req.difficulty());
        q.setPrompt(req.prompt().trim());
        q.setCodeSnippet(req.codeSnippet() == null || req.codeSnippet().isBlank() ? null : req.codeSnippet());
        q.setExplanation(req.explanation().trim());
        q.setStatus(req.status());
    }

    private static void validateOptions(List<AdminOption> options) {
        long correct = options.stream().filter(AdminOption::correct).count();
        if (correct != 1) throw ApiException.badRequest("ONE_CORRECT_OPTION", "Mark exactly one option as correct");
    }

    private static AdminQuestionView toView(Question q) {
        return new AdminQuestionView(q.getId(), q.getSlug(), q.getTopic().getSlug(), q.getDifficulty(), q.getPrompt(),
                q.getCodeSnippet(), q.getExplanation(), q.getStatus(),
                q.getOptions().stream().map(o -> new AdminOption(o.getBody(), o.isCorrect())).toList(), q.getUpdatedAt());
    }
}
