package com.swiftlabs.invoicing.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Let Spring Security answer (login redirect or the access-denied page). */
    @ExceptionHandler(AccessDeniedException.class)
    public void accessDenied(AccessDeniedException ex) {
        throw ex;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView notFound(ResourceNotFoundException ex) {
        return errorPage(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Business rule violations that a controller did not turn into a form error. */
    @ExceptionHandler(BusinessException.class)
    public ModelAndView business(BusinessException ex) {
        return errorPage(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Upload above spring.servlet.multipart.max-file-size (raised before any controller runs). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView uploadTooLarge(MaxUploadSizeExceededException ex) {
        return errorPage(HttpStatus.PAYLOAD_TOO_LARGE, "Le fichier envoyé est trop volumineux.");
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView unexpected(Exception ex) throws Exception {
        // 404, 405... carry their own status: leave them to Spring's error handling
        if (ex instanceof ErrorResponse) {
            throw ex;
        }
        log.error("Unhandled exception", ex);
        return errorPage(HttpStatus.INTERNAL_SERVER_ERROR,
            "Une erreur inattendue est survenue. Veuillez réessayer ou contacter l'administrateur.");
    }

    private ModelAndView errorPage(HttpStatus status, String message) {
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("message", message);
        return mav;
    }
}
