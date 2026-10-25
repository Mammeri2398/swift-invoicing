package com.swiftlabs.invoicing.mail;

/** How the connection to the SMTP server is protected. */
public enum MailSecurity {

    /** STARTTLS on the submission port (587): Gmail, Outlook, most providers. */
    STARTTLS,

    /** TLS from the start (port 465). */
    SSL,

    /** No encryption (a server on the local network). */
    NONE
}
