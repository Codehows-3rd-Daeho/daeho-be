package com.codehows.daehobe.logging.AOP.annotations;

import com.codehows.daehobe.logging.constant.ChangeType;
import com.codehows.daehobe.common.constant.TargetType;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.internal.engine.validationcontext.ValidationContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackChanges {
    ChangeType type();
    TargetType target();
    boolean trackMembers() default false;
}