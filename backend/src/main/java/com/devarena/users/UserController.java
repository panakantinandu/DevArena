package com.devarena.users;

import com.devarena.auth.AppUserPrincipal;
import com.devarena.users.UserDtos.MeResponse;
import com.devarena.users.UserDtos.PublicUser;
import com.devarena.users.UserDtos.UpdateProfileRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AppUserPrincipal me) {
        return users.me(me.id());
    }

    @PutMapping("/me")
    public MeResponse update(@AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody UpdateProfileRequest req) {
        return users.update(me.id(), req);
    }

    @GetMapping("/search")
    public List<PublicUser> search(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam("q") String q) {
        return users.search(q, me.id());
    }
}
