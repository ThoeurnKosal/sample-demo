package com.mysample.demo.shared.handler;

import com.mysample.demo.shared.model.FieldErrorDetail;
import java.util.ArrayList;
import java.util.List;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Turns Spring's two validation exceptions into one list of {@link FieldErrorDetail}.
 * <ul>
 *   <li>{@link MethodArgumentNotValidException}: {@code @Valid @RequestBody} failed on a method
 *       with no other parameter constraints.</li>
 *   <li>{@link HandlerMethodValidationException}: raised instead when the method also has
 *       constraints directly on parameters (e.g. {@code @Max} on a {@code @RequestParam});
 *       it then covers {@code @Valid} bodies too.</li>
 * </ul>
 */
final class ValidationErrorExtractor {

    private ValidationErrorExtractor() {
    }

    static List<FieldErrorDetail> from(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
    }

    static List<FieldErrorDetail> from(HandlerMethodValidationException ex) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors beanErrors) {
                beanErrors.getFieldErrors().forEach(error ->
                        errors.add(new FieldErrorDetail(error.getField(), error.getDefaultMessage())));
            } else {
                String parameter = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(error ->
                        errors.add(new FieldErrorDetail(parameter, error.getDefaultMessage())));
            }
        }
        return errors;
    }
}
