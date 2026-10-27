package com.swiftlabs.invoicing.access.user;

import com.swiftlabs.invoicing.common.Language;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/** Users form: the entity is never bound from the request. */
public class UserForm {

    private Long id;

    @NotBlank
    @Size(min = 3, max = 100)
    @Pattern(regexp = "[A-Za-z0-9._-]*")
    private String username;

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;
    private boolean enabled = true;

    /** The user's profile in each company the administrator manages (no profile: no access to it). */
    @Valid
    private List<Access> accesses = new ArrayList<>();

    /** Language of the interface and of the emails sent to this user. */
    private Language language;

    /** {@code accesses}: one row per company the administrator manages, with the user's profile there. */
    public static UserForm from(User user, List<Access> accesses) {
        UserForm form = new UserForm();
        form.setId(user.getId());
        form.setUsername(user.getUsername());
        form.setFirstName(user.getFirstName());
        form.setLastName(user.getLastName());
        form.setEmail(user.getEmail());
        form.setEnabled(user.isEnabled());
        form.setAccesses(new ArrayList<>(accesses));
        form.setLanguage(user.getLanguage());
        return form;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<Access> getAccesses() {
        return accesses;
    }

    public void setAccesses(List<Access> accesses) {
        this.accesses = accesses;
    }

    /** One company of the form: the profile chosen there, none for no access. */
    public static class Access {

        @NotNull
        private Long companyId;

        private Long profileId;

        public Access() {
        }

        public Access(Long companyId, Long profileId) {
            this.companyId = companyId;
            this.profileId = profileId;
        }

        public Long getCompanyId() { return companyId; }
        public void setCompanyId(Long companyId) { this.companyId = companyId; }
        public Long getProfileId() { return profileId; }
        public void setProfileId(Long profileId) { this.profileId = profileId; }
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }
}
