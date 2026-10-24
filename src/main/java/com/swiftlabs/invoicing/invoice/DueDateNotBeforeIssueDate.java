package com.swiftlabs.invoicing.invoice;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The due date, when set, is not before the issue date. The error is reported on "dueDate". */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DueDateNotBeforeIssueDate.Validator.class)
public @interface DueDateNotBeforeIssueDate {

    String message() default "L'échéance ne peut pas précéder la date d'émission.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<DueDateNotBeforeIssueDate, InvoiceForm> {

        @Override
        public boolean isValid(InvoiceForm form, ConstraintValidatorContext context) {
            if (form.getIssueDate() == null || form.getDueDate() == null || !form.getDueDate().isBefore(form.getIssueDate())) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("dueDate")
                .addConstraintViolation();
            return false;
        }
    }
}
