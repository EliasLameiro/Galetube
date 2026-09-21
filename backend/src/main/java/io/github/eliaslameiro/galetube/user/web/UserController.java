package io.github.eliaslameiro.galetube.user.web;

import io.github.eliaslameiro.galetube.user.dto.UserResponse;
import io.github.eliaslameiro.galetube.user.service.UserService;
import lombok.RequiredArgsConstructor;

import io.github.eliaslameiro.galetube.shared.security.Requester;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Requester requester) {
        return userService.getById(requester.id());
    }
}