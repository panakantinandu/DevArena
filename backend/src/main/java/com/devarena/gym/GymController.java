package com.devarena.gym;

import com.devarena.auth.AppUserPrincipal;
import com.devarena.gym.GymService.AnswerResult;
import com.devarena.gym.GymService.Overview;
import com.devarena.gym.GymService.RunView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/gym")
public class GymController {
    public record PracticeRequest(@NotNull GymService.Mode mode) {}

    public record AnswerRequest(@NotNull JsonNode response, @Min(50) @Max(99) int confidence) {}

    private final GymService gym;

    public GymController(GymService gym) {
        this.gym = gym;
    }

    @GetMapping
    public Overview overview(@AuthenticationPrincipal AppUserPrincipal me) {
        return gym.overview(me.id());
    }

    @PostMapping("/daily")
    public RunView daily(@AuthenticationPrincipal AppUserPrincipal me) {
        return gym.daily(me.id());
    }

    @PostMapping("/practice")
    public RunView practice(@AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody PracticeRequest req) {
        return gym.practice(me.id(), req.mode());
    }

    @GetMapping("/runs/{id}")
    public RunView run(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
        return gym.run(me.id(), id);
    }

    @PostMapping("/runs/{id}/puzzles/{puzzleId}/clues/{clueId}")
    public Map<String, String> clue(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id,
                                    @PathVariable UUID puzzleId, @PathVariable String clueId) {
        return gym.revealClue(me.id(), id, puzzleId, clueId);
    }

    @PostMapping("/runs/{id}/puzzles/{puzzleId}/answer")
    public AnswerResult answer(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id,
                               @PathVariable UUID puzzleId, @Valid @RequestBody AnswerRequest req) {
        return gym.answer(me.id(), id, puzzleId, req.response(), req.confidence());
    }
}
