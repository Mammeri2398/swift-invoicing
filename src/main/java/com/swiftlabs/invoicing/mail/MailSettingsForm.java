package com.swiftlabs.invoicing.mail;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Paramètres > Email. An empty password keeps the one already saved. */
public class MailSettingsForm {

    private boolean enabled;

    @Size(max = 255)
    private String host;

    @Min(1)
    @Max(65535)
    private Integer port;

    @NotNull
    private MailSecurity security = MailSecurity.STARTTLS;

    @Size(max = 255)
    private String username;

    @Size(max = 255)
    private String password;

    @Email
    @Size(max = 255)
    private String fromAddress;

    public static MailSettingsForm from(MailSettings settings) {
        MailSettingsForm form = new MailSettingsForm();
        form.setEnabled(settings.isEnabled());
        form.setHost(settings.getHost());
        form.setPort(settings.getPort());
        form.setSecurity(settings.getSecurity());
        form.setUsername(settings.getUsername());
        form.setFromAddress(settings.getFromAddress());
        return form;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host != null ? host.trim() : null; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public MailSecurity getSecurity() { return security; }
    public void setSecurity(MailSecurity security) { this.security = security; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username != null ? username.trim() : null; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress != null ? fromAddress.trim() : null; }
}
