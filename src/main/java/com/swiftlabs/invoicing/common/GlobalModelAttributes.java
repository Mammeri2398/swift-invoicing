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

    @ModelAttribute(CommonCodes.Attributes.AMOUNT_UTILS)
    public AmountUtils amountUtils() {
        return new AmountUtils();
    }

    @ModelAttribute(CommonCodes.Attributes.AMOUNT_IN_WORDS)
    public AmountInWords amountInWords() {
        return new AmountInWords();
    }

    @ModelAttribute(CommonCodes.Attributes.VAT_MENTION)
    public String vatMention() {
        return properties.vatMention();
    }

    /** Request path, used by the navigation to highlight the current section. */
    @ModelAttribute(CommonCodes.Attributes.CURRENT_PATH)
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
