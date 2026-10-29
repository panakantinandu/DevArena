package com.devarena.friends;

import com.devarena.auth.AppUserPrincipal;
import com.devarena.friends.FriendService.FriendsOverview;
import com.devarena.users.UserDtos.PublicProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FriendController {
    public record RequestBodyDto(@NotNull UUID userId) {}

    private final FriendService friends;

    public FriendController(FriendService friends) {
        this.friends = friends;
    }

    @GetMapping("/friends")
    public FriendsOverview overview(@AuthenticationPrincipal AppUserPrincipal me) {
        return friends.overview(me.id());
    }

    @PostMapping("/friends/requests")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void request(@AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody RequestBodyDto body) {
        friends.request(me.id(), body.userId());
    }

    @PostMapping("/friends/requests/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
        friends.accept(me.id(), id);
    }

    @DeleteMapping("/friends/requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID id) {
        friends.decline(me.id(), id);
    }

    @DeleteMapping("/friends/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID userId) {
        friends.remove(me.id(), userId);
    }

    @GetMapping("/users/{userId}/profile")
    public PublicProfile profile(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable UUID userId) {
        return friends.profile(me.id(), userId);
    }
}
