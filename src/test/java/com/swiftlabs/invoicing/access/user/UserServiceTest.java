package com.swiftlabs.invoicing.access.user;


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

    private UserForm newUser(String username, Long profileId) {
        UserForm form = new UserForm();
        form.setUsername(username);
        form.setEmail(username + "@example.test");
        form.setProfileId(profileId);
        return form;
    }

    @Test
    void createWithoutProfileIsRejected() {
        assertThatThrownBy(() -> userService.create(newUser("noprofile", null), PASSWORD))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Un profil doit être attribué à l'utilisateur.");
        assertThat(userRepository.existsByUsername("noprofile")).isFalse();
    }

    @Test
    void createWithUnknownProfileIsRejected() {
        assertThatThrownBy(() -> userService.create(newUser("ghost", 9999L), PASSWORD))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Profil introuvable.");
        assertThat(userRepository.existsByUsername("ghost")).isFalse();
    }

    @Test
    void createWithProfileSucceeds() {
        User created = userService.create(newUser("withprofile", 1L), PASSWORD);
        assertThat(created.getProfile().getName()).isEqualTo("Administrateur");
    }

    @Test
    void updateCannotRemoveProfile() {
        User created = userService.create(newUser("keepprofile", 1L), PASSWORD);

        UserForm edit = newUser("keepprofile", null);
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
