package com.swiftlabs.invoicing.web;

import com.swiftlabs.invoicing.util.AmountInWords;
import com.swiftlabs.invoicing.util.AmountUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {

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
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("message", ex.getMessage());
        return mav;
    }
}
