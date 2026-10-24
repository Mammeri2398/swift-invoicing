package com.swiftlabs.invoicing.invoice;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The email sent to the client with the invoice PDF attached. One recipient, plus an optional copy. */
public class InvoiceEmailForm {

    @NotBlank
    @Email
    @Size(max = 254)
    private String to;

    @Email
    @Size(max = 254)
    private String cc;

    @NotBlank
    @Size(max = 200)
    private String subject;

    @NotBlank
    @Size(max = 5000)
    private String message;

    public String getTo() { return to; }
    public void setTo(String to) { this.to = trim(to); }
    public String getCc() { return cc; }
    public void setCc(String cc) { this.cc = trim(cc); }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean hasCc() {
        return cc != null && !cc.isEmpty();
    }

    private static String trim(String value) {
        return value != null ? value.trim() : null;
    }
}
