package com.swiftlabs.invoicing;

import com.swiftlabs.invoicing.security.domain.Profile;
import com.swiftlabs.invoicing.security.domain.User;
import com.swiftlabs.invoicing.security.repository.UserRepository;
import com.swiftlabs.invoicing.security.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class UserServiceTest {

    private static final String PASSWORD = "Valid1234";

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User newUser(String username, Profile profile) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setProfile(profile);
        return user;
    }

    /** What the form binds from profile.id=... */
    private Profile profileRef(Long id) {
        Profile profile = new Profile();
        profile.setId(id);
        return profile;
    }

    @Test
    void createWithoutProfileIsRejected() {
        assertThatThrownBy(() -> userService.create(newUser("noprofile", null), PASSWORD))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Un profil doit être attribué à l'utilisateur.");
        // Empty select: profile object bound but without id
        assertThatThrownBy(() -> userService.create(newUser("noprofile", profileRef(null)), PASSWORD))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Un profil doit être attribué à l'utilisateur.");
        assertThat(userRepository.existsByUsername("noprofile")).isFalse();
    }

    @Test
    void createWithUnknownProfileIsRejected() {
        assertThatThrownBy(() -> userService.create(newUser("ghost", profileRef(9999L)), PASSWORD))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Profil introuvable.");
        assertThat(userRepository.existsByUsername("ghost")).isFalse();
    }

    @Test
    void createWithProfileSucceeds() {
        User created = userService.create(newUser("withprofile", profileRef(1L)), PASSWORD);
        assertThat(created.getProfile().getName()).isEqualTo("Administrateur");
    }

    @Test
    void updateCannotRemoveProfile() {
        User created = userService.create(newUser("keepprofile", profileRef(1L)), PASSWORD);

        User edit = newUser("keepprofile", null);
        edit.setId(created.getId());
        edit.setEnabled(true);
        assertThatThrownBy(() -> userService.update(edit))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(userRepository.findById(created.getId()).orElseThrow().getProfile().getId()).isEqualTo(1L);
    }

    @Test
    void databaseRejectsUserWithoutProfile() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO app_user (username, email, password) VALUES ('rawsql', 'rawsql@example.test', 'x')"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
