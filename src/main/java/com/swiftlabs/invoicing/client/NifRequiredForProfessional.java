package com.swiftlabs.invoicing.client;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A professional client must have a NIF. The error is reported on the "nif" field. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NifRequiredForProfessional.Validator.class)
public @interface NifRequiredForProfessional {

    String message() default "Le NIF est obligatoire pour un client professionnel.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<NifRequiredForProfessional, ClientForm> {

        @Override
        public boolean isValid(ClientForm form, ConstraintValidatorContext context) {
            if (form.getType() != ClientType.PROFESSIONNEL || (form.getNif() != null && !form.getNif().isBlank())) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("nif")
                .addConstraintViolation();
            return false;
        }
    }
}
