package com.swiftlabs.invoicing.common;

import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Text submitted in the forms is trimmed, and blank text becomes null (stored as NULL, not "").
 * Limited to the form objects by name: passwords (request parameters) are never trimmed.
 */
@ControllerAdvice
public class FormBindingAdvice {

    @InitBinder({"client", "invoice", "issuer", "user", "profile", "privilege", "filter"})
    public void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }
}
