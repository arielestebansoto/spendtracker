package com.arielsoto.spendtracker.web;

import java.util.List;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.arielsoto.spendtracker.aiusage.AiUsageLimitExceededException;
import com.arielsoto.spendtracker.classificationrule.InvalidCategoryException;
import com.arielsoto.spendtracker.classificationrule.RuleLimitExceededException;
import com.arielsoto.spendtracker.classificationrule.RuleNotFoundException;
import com.arielsoto.spendtracker.loggin.SecurityMdcFilter;
import com.arielsoto.spendtracker.receipt.ReceiptUploadRateLimitException;
import com.arielsoto.spendtracker.receipt.ReceiptValidationException;
import com.arielsoto.spendtracker.receipt.SpendNotRetryableException;
import com.arielsoto.spendtracker.spend.SpendInProgressException;
import com.arielsoto.spendtracker.spend.SpendNotFoundException;
import com.arielsoto.spendtracker.spend.SpendNotReclassifiableException;
import com.arielsoto.spendtracker.spend.SpendWithoutReceiptException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns every error a controller raises into an RFC 9457 Problem Detail (application/problem+json
 * with type, title, status, detail and instance). Extending ResponseEntityExceptionHandler gives
 * Spring MVC's own errors (validation, unknown path, 405, 413 on a receipt over the multipart
 * limit) the same format. This class replaces the handler that spring.mvc.problemdetails.enabled
 * registers, so that property is not set.
 *
 * Not covered: errors from the Spring Security filters (401, 403 on a missing CSRF token), which
 * are answered before the request reaches a controller.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({SpendNotFoundException.class, RuleNotFoundException.class})
    ProblemDetail handleNotFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({InvalidCategoryException.class, SpendWithoutReceiptException.class})
    ProblemDetail handleBadRequest(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({
        RuleLimitExceededException.class,
        SpendInProgressException.class,
        SpendNotReclassifiableException.class,
        SpendNotRetryableException.class
    })
    ProblemDetail handleConflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    // The receipt upload errors carry a `reason` (and `window` or `resourceType`), which the
    // frontend reads to pick the message it shows (frontend/src/app/lib/receipt.ts).

    @ExceptionHandler(ReceiptValidationException.class)
    ProblemDetail handleInvalidReceipt(ReceiptValidationException e) {
        log.warn("api_create_spend_from_receipt_invalid_file userId={} reason={}",
            currentUserId(), e.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setProperty("reason", "invalid_file");
        return problem;
    }

    @ExceptionHandler(ReceiptUploadRateLimitException.class)
    ProblemDetail handleUploadRateLimit(ReceiptUploadRateLimitException e) {
        log.warn("api_create_spend_from_receipt_rate_limited userId={} window={}",
            currentUserId(), e.getWindow());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        problem.setProperty("reason", "rate_limit");
        problem.setProperty("window", e.getWindow());
        return problem;
    }

    @ExceptionHandler(AiUsageLimitExceededException.class)
    ProblemDetail handleAiUsageLimit(AiUsageLimitExceededException e) {
        log.warn("api_create_spend_from_receipt_ai_limit_exceeded userId={} resourceType={}",
            currentUserId(), e.getResourceType());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        problem.setProperty("reason", "ai_quota");
        problem.setProperty("resourceType", e.getResourceType());
        return problem;
    }

    /** Anything else is a bug or an AWS failure: logged in full, answered with a generic detail. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("api_unexpected_error method={} path={}", request.getMethod(), request.getRequestURI(), e);

        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    /** Adds the invalid fields to the default 400 body of a failed @Valid. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
            .toList();
        ex.getBody().setProperty("errors", errors);

        return handleExceptionInternal(ex, null, headers, status, request);
    }

    record FieldErrorDetail(String field, String message) {
    }

    // SecurityMdcFilter puts the user's id in the MDC for the whole request.
    private static String currentUserId() {
        return MDC.get(SecurityMdcFilter.USER_ID);
    }
}
