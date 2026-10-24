package com.swiftlabs.invoicing.access.user;

/** Users form: the entity is never bound from the request. */
public class UserForm {

    private Long id;
    private String username;
    private String email;
    private boolean enabled = true;
    private Long profileId;

    public static UserForm from(User user) {
        UserForm form = new UserForm();
        form.setId(user.getId());
        form.setUsername(user.getUsername());
        form.setEmail(user.getEmail());
        form.setEnabled(user.isEnabled());
        form.setProfileId(user.getProfile() != null ? user.getProfile().getId() : null);
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

    public Long getProfileId() {
        return profileId;
    }

    public void setProfileId(Long profileId) {
        this.profileId = profileId;
    }
}
