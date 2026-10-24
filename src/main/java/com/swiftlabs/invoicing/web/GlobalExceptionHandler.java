package com.swiftlabs.invoicing.web;

import com.swiftlabs.invoicing.util.AmountInWords;
import com.swiftlabs.invoicing.util.AmountUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ModelAttribute("amountUtils")
    public AmountUtils amountUtils() {
        return new AmountUtils();
    }

    @ModelAttribute("amountInWords")
    public AmountInWords amountInWords() {
        return new AmountInWords();
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("message", ex.getMessage());
        return mav;
    }
}
