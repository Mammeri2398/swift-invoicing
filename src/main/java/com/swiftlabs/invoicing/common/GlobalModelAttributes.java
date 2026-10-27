package com.swiftlabs.invoicing.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Helpers available to every view (the TVA exemption mention: see BusinessProfileAdvice). */
@ControllerAdvice
public class GlobalModelAttributes {

    @ModelAttribute(CommonCodes.Attributes.AMOUNT_UTILS)
    public AmountUtils amountUtils() {
        return new AmountUtils();
    }

    @ModelAttribute(CommonCodes.Attributes.AMOUNT_IN_WORDS)
    public AmountInWords amountInWords() {
        return new AmountInWords();
    }

    /** Request path, used by the navigation to highlight the current section. */
    @ModelAttribute(CommonCodes.Attributes.CURRENT_PATH)
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
