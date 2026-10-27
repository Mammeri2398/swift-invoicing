package com.swiftlabs.invoicing.access.user;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.company.CompanyAccessService;
import com.swiftlabs.invoicing.company.CompanySchemas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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
    private CompanyAccessService accessService;

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
        // A profile in the first company, or no company at all
        form.setAccesses(new ArrayList<>(List.of(new UserForm.Access(CompanySchemas.FIRST_ID, profileId))));
        return form;
    }

    @Test
    void createGeneratesATemporaryPasswordToChangeAtFirstLogin() {
        UserService.NewAccount account = userService.create(newUser("newbie", 1L), null);

        User user = account.user();
        assertThat(user.isMustChangePassword()).isTrue();
        assertThat(user.getPassword()).startsWith("{argon2}");
        assertThat(passwordEncoder.matches(account.temporaryPassword(), user.getPassword())).isTrue();
        passwordPolicy.validate(account.temporaryPassword(), "newbie");
        assertThat(userService.create(newUser("other", 1L), null).temporaryPassword()).isNotEqualTo(account.temporaryPassword());
    }

    @Test
    void createWithoutProfileIsRejected() {
        assertThatThrownBy(() -> userService.create(newUser("noprofile", null), null))
            .isInstanceOf(BusinessException.class)
            .hasMessageStartingWith("Choisissez un profil dans au moins une société");
        assertThat(userRepository.existsByUsername("noprofile")).isFalse();
    }

    @Test
    void createWithUnknownProfileIsRejected() {
        assertThatThrownBy(() -> userService.create(newUser("ghost", 9999L), null))
            .isInstanceOf(BusinessException.class)
            .hasMessage("Profil introuvable.");
        assertThat(userRepository.existsByUsername("ghost")).isFalse();
    }

    @Test
    void updateCannotLeaveTheUserWithoutAnyCompany() {
        User created = userService.create(newUser("keepprofile", 1L), null).user();

        UserForm edit = newUser("keepprofile", null);
        edit.setId(created.getId());
        assertThatThrownBy(() -> userService.update(edit, null))
            .isInstanceOf(BusinessException.class);
        assertThat(accessService.profileIn(created.getId(), CompanySchemas.FIRST_ID).orElseThrow().getId()).isEqualTo(1L);
    }

    @Test
    void administratorCannotDisableOrDeleteTheirOwnAccount() {
        User me = userService.create(newUser("selfadmin", 1L), null).user();

        UserForm disableMe = UserForm.from(me, List.of(new UserForm.Access(CompanySchemas.FIRST_ID, 1L)));
        disableMe.setEnabled(false);
        assertThatThrownBy(() -> userService.update(disableMe, me.getId()))
            .hasMessage("Vous ne pouvez pas désactiver votre propre compte.");
        assertThatThrownBy(() -> userService.delete(me.getId(), me.getId()))
            .hasMessage("Vous ne pouvez pas supprimer votre propre compte.");
        assertThat(userRepository.findById(me.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void changingOwnPasswordRequiresTheCurrentOne() {
        UserService.NewAccount account = userService.create(newUser("changer", 1L), null);
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
        assertThatThrownBy(() -> passwordPolicy.validate("short1A", "bob")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> passwordPolicy.validate("alllowercase1", "bob")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> passwordPolicy.validate("A1" + "a".repeat(200), "bob")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> passwordPolicy.validate("Martin2026x", "martin"))
            .hasMessage("Le mot de passe ne doit pas contenir le nom d'utilisateur.");
        passwordPolicy.validate("Correct1Horse", "bob");
    }

    @Test
    void adminResetGivesANewTemporaryPasswordAndUnlocks() {
        User user = userService.create(newUser("locked", 1L), null).user();
        user.recordFailedLogin(1, java.time.Duration.ofMinutes(15), java.time.LocalDateTime.now());
        assertThat(user.getLockedUntil()).isNotNull();

        String temporary = userService.resetToTemporaryPassword(user.getId());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getLockedUntil()).isNull();
        assertThat(reloaded.isMustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches(temporary, reloaded.getPassword())).isTrue();
    }

    @Test
    void databaseRejectsAnAccessWithoutProfile() {
        User created = userService.create(newUser("noprofile", 1L), null).user();
        userRepository.flush();
        // The profile is per company (user_company): an access always has one
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE public.user_company SET profile_id = NULL WHERE user_id = ?", created.getId()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
