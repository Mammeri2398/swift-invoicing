package com.swiftlabs.invoicing.mail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The SMTP server set in Paramètres > Email (single row, id = 1). */
@Entity
@Table(name = "mail_settings")
public class MailSettings {

    static final long ID = 1L;

    @Id
    private Long id = ID;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "host")
    private String host;

    @Column(name = "port")
    private Integer port;

    @Enumerated(EnumType.STRING)
    @Column(name = "security", nullable = false, length = 20)
    private MailSecurity security = MailSecurity.STARTTLS;

    @Column(name = "username")
    private String username;

    /** Stored as typed: the SMTP server needs it in clear. Never shown back on the page. */
    @Column(name = "password")
    private String password;

    @Column(name = "from_address")
    private String fromAddress;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public MailSecurity getSecurity() { return security; }
    public void setSecurity(MailSecurity security) { this.security = security; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

    /** The sender: the "from" address, or the account (most servers only accept their own account). */
    public String sender() {
        return fromAddress != null && !fromAddress.isBlank() ? fromAddress : (username != null ? username : "");
    }
}
