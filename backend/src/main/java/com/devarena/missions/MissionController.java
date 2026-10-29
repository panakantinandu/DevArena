package com.devarena.missions;

import com.devarena.auth.AppUserPrincipal;
import com.devarena.missions.MissionDtos.MissionDetail;
import com.devarena.missions.MissionDtos.SubmitRequest;
import com.devarena.missions.MissionDtos.SubmitResult;
import com.devarena.missions.MissionDtos.TodayResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/missions")
public class MissionController {
    private final MissionService missions;

    public MissionController(MissionService missions) {
        this.missions = missions;
    }

    @GetMapping("/today")
    public TodayResponse today(@AuthenticationPrincipal AppUserPrincipal me) {
        return missions.today(me.id());
    }

    @GetMapping("/{id}")
    public MissionDetail detail(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
        return missions.detail(me.id(), id);
    }

    @PostMapping("/{id}/submit")
    public SubmitResult submit(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id,
                               @Valid @RequestBody SubmitRequest req) {
        return missions.submit(me.id(), id, req);
    }
}
