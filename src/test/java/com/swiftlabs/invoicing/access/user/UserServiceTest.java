package com.swiftlabs.invoicing.access.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordPolicy passwordPolicy;

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
    void createGeneratesATemporaryPasswordToChangeAtFirstLogin() {
        UserService.NewAccount account = userService.create(newUser("newbie", 1L));

        User user = account.user();
        assertThat(user.isMustChangePassword()).isTrue();
        assertThat(user.getPassword()).startsWith("{argon2}");
        assertThat(passwordEncoder.matches(account.temporaryPassword(), user.getPassword())).isTrue();
        passwordPolicy.validate(account.temporaryPassword(), "newbie");
        assertThat(userService.create(newUser("other", 1L)).temporaryPassword()).isNotEqualTo(account.temporaryPassword());
    }

    @Test
    void createWithoutProfileIsRejected() {
        assertThatThrownBy(() -> userService.create(newUser("noprofile", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Un profil doit être attribué à l'utilisateur.");
        assertThat(userRepository.existsByUsername("noprofile")).isFalse();
    }

    @Test
    void createWithUnknownProfileIsRejected() {
        assertThatThrownBy(() -> userService.create(newUser("ghost", 9999L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Profil introuvable.");
        assertThat(userRepository.existsByUsername("ghost")).isFalse();
    }

    @Test
    void updateCannotRemoveProfile() {
        User created = userService.create(newUser("keepprofile", 1L)).user();

        UserForm edit = newUser("keepprofile", null);
        edit.setId(created.getId());
        assertThatThrownBy(() -> userService.update(edit, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(userRepository.findById(created.getId()).orElseThrow().getProfile().getId()).isEqualTo(1L);
    }

    @Test
    void administratorCannotDisableOrDeleteTheirOwnAccount() {
        User me = userService.create(newUser("selfadmin", 1L)).user();

        UserForm disableMe = UserForm.from(me);
        disableMe.setEnabled(false);
        assertThatThrownBy(() -> userService.update(disableMe, me.getId()))
            .hasMessage("Vous ne pouvez pas désactiver votre propre compte.");
        assertThatThrownBy(() -> userService.delete(me.getId(), me.getId()))
            .hasMessage("Vous ne pouvez pas supprimer votre propre compte.");
        assertThat(userRepository.findById(me.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void changingOwnPasswordRequiresTheCurrentOne() {
        UserService.NewAccount account = userService.create(newUser("changer", 1L));
        Long id = account.user().getId();

        assertThatThrownBy(() -> userService.changeOwnPassword(id, "wrong", "NewPass456"))
            .hasMessage("Le mot de passe actuel est incorrect.");
        assertThatThrownBy(() -> userService.changeOwnPassword(id, account.temporaryPassword(), account.temporaryPassword()))
            .hasMessage("Le nouveau mot de passe doit être différent de l'actuel.");

        userService.changeOwnPassword(id, account.temporaryPassword(), "NewPass456");
        User user = userRepository.findById(id).orElseThrow();
        assertThat(passwordEncoder.matches("NewPass456", user.getPassword())).isTrue();
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.getPasswordChangedAt()).isNotNull();
    }

    @Test
    void passwordPolicyRejectsWeakPasswordsAndTheUsername() {
        assertThatThrownBy(() -> passwordPolicy.validate("short1A", "bob")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> passwordPolicy.validate("alllowercase1", "bob")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> passwordPolicy.validate("A1" + "a".repeat(200), "bob")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> passwordPolicy.validate("Martin2026x", "martin"))
            .hasMessage("Le mot de passe ne doit pas contenir le nom d'utilisateur.");
        passwordPolicy.validate("Correct1Horse", "bob");
    }

    @Test
    void adminResetGivesANewTemporaryPasswordAndUnlocks() {
        User user = userService.create(newUser("locked", 1L)).user();
        user.recordFailedLogin(1, java.time.Duration.ofMinutes(15), java.time.LocalDateTime.now());
        assertThat(user.getLockedUntil()).isNotNull();

        String temporary = userService.resetToTemporaryPassword(user.getId());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getLockedUntil()).isNull();
        assertThat(reloaded.isMustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches(temporary, reloaded.getPassword())).isTrue();
    }

    @Test
    void databaseRejectsUserWithoutProfile() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO app_user (username, email, password) VALUES ('rawsql', 'rawsql@example.test', 'x')"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
