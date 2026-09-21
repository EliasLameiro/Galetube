package io.github.eliaslameiro.galetube.user.service;

import io.github.eliaslameiro.galetube.user.domain.User;
import io.github.eliaslameiro.galetube.user.domain.UserRepository;
import io.github.eliaslameiro.galetube.user.dto.UserResponse;
import io.github.eliaslameiro.galetube.user.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return userRepository.findById(id).map(UserResponse::from).orElseThrow(() -> new UserNotFoundException("No existe el usuario con id " + id));
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> getUsernamesByIds(Collection<UUID> ids) {
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId,  User::getUsername));
    }

    @Transactional(readOnly = true)
    public Optional<String> findUsernameById(UUID id) {
        return userRepository.findById(id).map(User::getUsername);
    }

 }
