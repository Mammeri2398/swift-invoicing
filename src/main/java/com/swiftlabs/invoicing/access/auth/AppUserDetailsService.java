package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Loads users for form login, and stores re-encoded hashes when the password encoder
 * upgrades them at login (e.g. legacy hashes without an {id} prefix).
 */
@Service
public class AppUserDetailsService implements UserDetailsService, UserDetailsPasswordService {

    private final UserRepository userRepository;
    private final Clock clock;

    public AppUserDetailsService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        User user = userRepository.findByUsernameWithPrivileges(username)
            .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        return AppUserPrincipal.of(user, LocalDateTime.now(clock));
    }

    @Override
    @Transactional
    public UserDetails updatePassword(UserDetails details, String newPasswordHash) {
        User user = userRepository.findByUsernameWithPrivileges(details.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        user.setPassword(newPasswordHash);
        return AppUserPrincipal.of(user, LocalDateTime.now(clock));
    }
}
