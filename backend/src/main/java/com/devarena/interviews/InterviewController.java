package com.devarena.interviews;

import com.devarena.auth.AppUserPrincipal;
import com.devarena.interviews.InterviewDtos.CodeSnapshot;
import com.devarena.interviews.InterviewDtos.CreateRequest;
import com.devarena.interviews.InterviewDtos.EvaluationRequest;
import com.devarena.interviews.InterviewDtos.HistoryItem;
import com.devarena.interviews.InterviewDtos.InterviewerView;
import com.devarena.interviews.InterviewDtos.InviteRequest;
import com.devarena.interviews.InterviewDtos.JoinRequest;
import com.devarena.interviews.InterviewDtos.NoteRequest;
import com.devarena.interviews.InterviewDtos.Report;
import com.devarena.interviews.InterviewDtos.RubricCriterion;
import com.devarena.interviews.InterviewDtos.SessionView;
import com.devarena.interviews.InterviewDtos.SubmitCodeRequest;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {
    private final InterviewService interviews;

    public InterviewController(InterviewService interviews) {
        this.interviews = interviews;
    }

    @GetMapping("/types")
    public List<Map<String, String>> types() {
        return Arrays.stream(InterviewType.values()).map(t -> Map.of("value", t.name(), "label", t.label())).toList();
    }

    @GetMapping("/rubric")
    public List<RubricCriterion> rubric() {
        return InterviewService.RUBRIC;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionView create(@AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody CreateRequest req) {
        return interviews.create(me.id(), req);
    }

    @GetMapping
    public List<HistoryItem> history(@AuthenticationPrincipal AppUserPrincipal me) {
        return interviews.history(me.id());
    }

    @PostMapping("/join")
    public SessionView join(@AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody JoinRequest req) {
        return interviews.join(me.id(), req.code());
    }

    @GetMapping("/{id}")
    public SessionView view(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
        return interviews.view(me.id(), id);
    }

    /** Interviewer-only: answer guides, rubric notes and private notes. 403 for the candidate. */
    @GetMapping("/{id}/interviewer")
    public InterviewerView interviewer(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
        return interviews.interviewerView(me.id(), id);
    }

    @PostMapping("/{id}/start")
    public SessionView start(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
        return interviews.start(me.id(), id);
    }

    @PostMapping("/{id}/next")
    public SessionView next(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
        return interviews.next(me.id(), id);
    }

    @PostMapping("/{id}/finish")
    public SessionView finish(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
        return interviews.finish(me.id(), id);
    }

    @PostMapping("/{id}/cancel")
    public SessionView cancel(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
        return interviews.cancel(me.id(), id);
    }

    @PostMapping("/{id}/rematch")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionView rematch(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
        return interviews.rematch(me.id(), id);
    }

    @PostMapping("/{id}/invite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void invite(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id,
                       @Valid @RequestBody InviteRequest req) {
        interviews.invite(me.id(), id, req.userId());
    }

    @PutMapping("/{id}/notes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void note(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id, @Valid @RequestBody NoteRequest req) {
        interviews.saveNote(me.id(), id, req);
    }

    @PostMapping("/{id}/evaluation")
    public Report evaluate(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id,
                           @Valid @RequestBody EvaluationRequest req) {
        return interviews.evaluate(me.id(), id, req);
    }

    @GetMapping("/{id}/report")
    public Report report(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
        return interviews.report(me.id(), id);
    }

    @GetMapping("/{id}/code")
    public CodeSnapshot code(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
        return interviews.code(me.id(), id);
    }

    @PostMapping("/{id}/code/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public void submitCode(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id,
                           @Valid @RequestBody SubmitCodeRequest req) {
        interviews.submitCode(me.id(), id, req);
    }
}
