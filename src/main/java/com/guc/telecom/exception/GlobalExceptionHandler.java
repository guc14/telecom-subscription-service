package com.guc.telecom.exception;

import com.guc.telecom.dto.ExceptionResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * GlobalExceptionHandler — maps all business exceptions to a consistent
 * JSON error response shape:
 *
 * {
 *   "success": false,
 *   "errorCode": "DUPLICATE_SUBSCRIPTION",
 *   "message": "Customer 3 is already subscribed to plan 7",
 *   "path": "/plans/7/customers/3/activate"
 * }
 *
 * Error code → HTTP status mapping:
 *   CustomerNotFoundException        404  CUSTOMER_NOT_FOUND
 *   ServicePlanNotFoundException     404  PLAN_NOT_FOUND
 *   DuplicateSubscriptionException   409  DUPLICATE_SUBSCRIPTION
 *   PlanCapacityExceededException    409  PLAN_CAPACITY_EXCEEDED
 *   DuplicateProfileException        409  DUPLICATE_PROFILE
 *   MethodArgumentNotValidException  400  VALIDATION_ERROR
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomerNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ExceptionResponse handleCustomerNotFound(CustomerNotFoundException ex,
                                                     HttpServletRequest request) {
        return new ExceptionResponse("CUSTOMER_NOT_FOUND", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ServicePlanNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ExceptionResponse handlePlanNotFound(ServicePlanNotFoundException ex,
                                                 HttpServletRequest request) {
        return new ExceptionResponse("PLAN_NOT_FOUND", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(DuplicateSubscriptionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ExceptionResponse handleDuplicateSubscription(DuplicateSubscriptionException ex,
                                                          HttpServletRequest request) {
        return new ExceptionResponse("DUPLICATE_SUBSCRIPTION", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(PlanCapacityExceededException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ExceptionResponse handleCapacityExceeded(PlanCapacityExceededException ex,
                                                     HttpServletRequest request) {
        return new ExceptionResponse("PLAN_CAPACITY_EXCEEDED", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(DuplicateProfileException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ExceptionResponse handleDuplicateProfile(DuplicateProfileException ex,
                                                     HttpServletRequest request) {
        return new ExceptionResponse("DUPLICATE_PROFILE", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ExceptionResponse handleValidation(MethodArgumentNotValidException ex,
                                               HttpServletRequest request) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return new ExceptionResponse("VALIDATION_ERROR", details, request.getRequestURI());
    }
}
