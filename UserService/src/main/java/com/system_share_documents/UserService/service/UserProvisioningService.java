package com.system_share_documents.UserService.service;

import com.system_share_documents.UserService.entity.User;

import com.system_share_documents.UserService.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserProvisioningService {
    private final UserRepository userRepository;

    @Transactional
    public User ensureUser(Authentication auth) {
        Jwt jwt = (Jwt) auth.getPrincipal();
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        String fullName = jwt.getClaimAsString("name");

        Optional<User> byUsername = userRepository.findByUsername(username);
        if (byUsername.isPresent()) return byUsername.get();

        User u = User.builder()
                .username(username != null ? username : jwt.getSubject())
                .email(email != null ? email : (jwt.getSubject() + "@unknown.local"))
                .fullName(fullName)
                .status((short)1)
                .createdAt(Timestamp.from(Instant.now()))
                .updatedAt(Timestamp.from(Instant.now()))
                .build();

        return userRepository.save(u);
    }
}