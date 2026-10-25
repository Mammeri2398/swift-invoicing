package com.swiftlabs.invoicing.access.account;

import com.swiftlabs.invoicing.access.user.User;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The personal information a user edits themselves. The username and the profile are set by an administrator. */
public class AccountForm {

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    public static AccountForm from(User user) {
        AccountForm form = new AccountForm();
        form.setFirstName(user.getFirstName());
        form.setLastName(user.getLastName());
        form.setEmail(user.getEmail());
        return form;
    }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email != null ? email.trim() : null; }
}
