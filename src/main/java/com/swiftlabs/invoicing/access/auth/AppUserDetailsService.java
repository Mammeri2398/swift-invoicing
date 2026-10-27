package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.company.CompanyAccess;
import com.swiftlabs.invoicing.company.CompanyAccessRepository;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Loads users for form login, and stores re-encoded hashes when the password encoder
 * upgrades them at login (e.g. legacy hashes without an {id} prefix).
 *
 * The user starts in the company they worked in last, or in their only company; with several and none used yet,
 * they choose one after login (CompanyFilter).
 */
@Service
public class AppUserDetailsService implements UserDetailsService, UserDetailsPasswordService {

    private final UserRepository userRepository;
    private final CompanyAccessRepository accessRepository;
    private final Clock clock;

    public AppUserDetailsService(UserRepository userRepository, CompanyAccessRepository accessRepository, Clock clock) {
        this.userRepository = userRepository;
        this.accessRepository = accessRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        return AppUserPrincipal.of(user, startingCompany(user), LocalDateTime.now(clock));
    }

    @Override
    @Transactional
    public UserDetails updatePassword(UserDetails details, String newPasswordHash) {
        User user = userRepository.findByUsername(details.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        user.setPassword(newPasswordHash);
        return AppUserPrincipal.of(user, startingCompany(user), LocalDateTime.now(clock));
    }

    /** The company worked in last if still open to the user, else their only one; null: to choose. */
    private CompanyAccess startingCompany(User user) {
        List<CompanyAccess> accesses = accessRepository.findActiveByUserId(user.getId());
        return accesses.stream()
            .filter(access -> access.getCompany().getId().equals(user.getLastCompanyId()))
            .findFirst()
            .orElse(accesses.size() == 1 ? accesses.get(0) : null);
    }
}
