package com.swiftlabs.invoicing.security.service;

import com.swiftlabs.invoicing.security.domain.User;
import com.swiftlabs.invoicing.security.repository.UserRepository;
import com.swiftlabs.invoicing.security.util.PasswordPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       @Value("${security.password.min-length:8}") int minLength) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = new PasswordPolicy(minLength);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    public User create(User user, String rawPassword) {
        validatePassword(rawPassword);
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new IllegalArgumentException("Ce nom d'utilisateur existe déjà.");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("Cet email est déjà utilisé.");
        }
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setEnabled(true);
        return userRepository.save(user);
    }

    @Transactional
    public User update(User user) {
        User existing = userRepository.findById(user.getId())
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
        existing.setEmail(user.getEmail());
        existing.setEnabled(user.isEnabled());
        existing.setProfile(user.getProfile());
        return userRepository.save(existing);
    }

    @Transactional
    public void changePassword(Long userId, String rawPassword) {
        validatePassword(rawPassword);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
        user.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }

    @Transactional
    public void delete(Long id) {
        userRepository.deleteById(id);
    }

    private void validatePassword(String password) {
        if (!passwordPolicy.isValid(password)) {
            throw new IllegalArgumentException(passwordPolicy.getErrorMessage());
        }
    }
}
