package com.swiftlabs.invoicing.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Messages messages;

    public GlobalExceptionHandler(Messages messages) {
        this.messages = messages;
    }

    /** Let Spring Security answer (login redirect or the access-denied page). */
    @ExceptionHandler(AccessDeniedException.class)
    public void accessDenied(AccessDeniedException ex) {
        throw ex;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView notFound(ResourceNotFoundException ex) {
        return errorPage(HttpStatus.NOT_FOUND, messages.of(ex));
    }

    /** Business rule violations that a controller did not turn into a form error. */
    @ExceptionHandler(BusinessException.class)
    public ModelAndView business(BusinessException ex) {
        return errorPage(HttpStatus.BAD_REQUEST, messages.of(ex));
    }

    /** A document changed by someone else between the moment it was read and this change: nothing was saved. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ModelAndView modifiedMeanwhile(ObjectOptimisticLockingFailureException ex) {
        log.info("Concurrent change refused: {}", ex.getMessage());
        return errorPage(HttpStatus.CONFLICT, messages.get(MessageProperties.INVOICE_ERROR_MODIFIED_MEANWHILE));
    }

    /** An address with a malformed id (/users/abc/edit): a page that does not exist, not a server error. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView badParameter(MethodArgumentTypeMismatchException ex) {
        return errorPage(HttpStatus.NOT_FOUND, messages.get(MessageProperties.ERROR_NOT_FOUND));
    }

    /** The browser left before the answer was written (page closed, link clicked twice): nothing to show, nor to log. */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void clientGone(AsyncRequestNotUsableException ex) {
        log.debug("Client gone: {}", ex.getMessage());
    }

    /** Upload above spring.servlet.multipart.max-file-size (raised before any controller runs). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView uploadTooLarge(MaxUploadSizeExceededException ex) {
        return errorPage(HttpStatus.PAYLOAD_TOO_LARGE, messages.get(MessageProperties.ERROR_UPLOAD_TOO_LARGE));
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView unexpected(Exception ex) throws Exception {
        // 404, 405... carry their own status: leave them to Spring's error handling
        if (ex instanceof ErrorResponse) {
            throw ex;
        }
        log.error("Unhandled exception", ex);
        return errorPage(HttpStatus.INTERNAL_SERVER_ERROR, messages.get(MessageProperties.ERROR_UNEXPECTED));
    }

    private ModelAndView errorPage(HttpStatus status, String message) {
        ModelAndView mav = new ModelAndView(CommonCodes.Views.ERROR);
        mav.setStatus(status);
        mav.addObject(CommonCodes.Attributes.STATUS, status.value());
        mav.addObject(CommonCodes.Attributes.MESSAGE, message);
        return mav;
    }
}
