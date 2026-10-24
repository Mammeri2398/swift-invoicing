package com.swiftlabs.invoicing.common;

import com.swiftlabs.invoicing.config.InvoiceProperties;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Helpers and settings available to every view. */
@ControllerAdvice
public class GlobalModelAttributes {

    private final InvoiceProperties properties;

    public GlobalModelAttributes(InvoiceProperties properties) {
        this.properties = properties;
    }

    @ModelAttribute("amountUtils")
    public AmountUtils amountUtils() {
        return new AmountUtils();
    }

    @ModelAttribute("amountInWords")
    public AmountInWords amountInWords() {
        return new AmountInWords();
    }

    @ModelAttribute("vatMention")
    public String vatMention() {
        return properties.vatMention();
    }

    /** Request path, used by the navigation to highlight the current section. */
    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
