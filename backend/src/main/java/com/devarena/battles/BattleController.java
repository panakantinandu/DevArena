package com.devarena.battles;

import com.devarena.auth.AppUserPrincipal;
import com.devarena.battles.BattleDtos.AnswerAck;
import com.devarena.battles.BattleDtos.AnswerRequest;
import com.devarena.battles.BattleDtos.BattleResults;
import com.devarena.battles.BattleDtos.BattleView;
import com.devarena.battles.BattleDtos.CreateBattleRequest;
import com.devarena.battles.BattleDtos.HistoryItem;
import com.devarena.battles.BattleDtos.JoinRequest;
import com.devarena.battles.BattleDtos.ReadyRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/battles")
public class BattleController {
    private final BattleService battles;

    public BattleController(BattleService battles) {
        this.battles = battles;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BattleView create(@AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody CreateBattleRequest req) {
        return battles.create(me.id(), req);
    }

    @GetMapping
    public List<HistoryItem> history(@AuthenticationPrincipal AppUserPrincipal me) {
        return battles.history(me.id());
    }

    @GetMapping("/code/{code}")
    public Map<String, Object> preview(@PathVariable String code) {
        return battles.preview(code);
    }

    @PostMapping("/join")
    public BattleView join(@AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody JoinRequest req) {
        return battles.join(me.id(), req.code());
    }

    @GetMapping("/{id}")
    public BattleView view(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
        return battles.view(me.id(), id);
    }

    @PostMapping("/{id}/ready")
    public BattleView ready(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id,
                            @RequestBody ReadyRequest req) {
        return battles.setReady(me.id(), id, req.ready());
    }

    @PostMapping("/{id}/start")
    public BattleView start(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
        return battles.start(me.id(), id);
    }

    @PostMapping("/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
        battles.leave(me.id(), id);
    }

    @PostMapping("/{id}/answer")
    public AnswerAck answer(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id,
                            @Valid @RequestBody AnswerRequest req) {
        return battles.answer(me.id(), id, req.questionIndex(), req.optionId());
    }

    @GetMapping("/{id}/results")
    public BattleResults results(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
        return battles.results(me.id(), id);
    }
}
