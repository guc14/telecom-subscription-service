package com.guc.telecom.aop;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * AOP aspect that logs entry, exit time, and errors for all controller methods.
 *
 * HttpServletRequest/Response args are skipped in argument logging to avoid
 * dumping large or sensitive objects. AOP is used here because logging is a
 * cross-cutting concern with no business logic — adding a new controller
 * automatically gets request logging without any extra code.
 */
@Aspect
@Component
public class LoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    @Around("execution(* com.guc.telecom.controller..*(..))")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        String method = sig.getDeclaringType().getSimpleName() + "." + sig.getName();

        String args = Arrays.stream(joinPoint.getArgs())
                .map(this::safeToString)
                .collect(Collectors.joining(", "));

        logger.info("{} args=[{}]", method, args);

        try {
            Object result = joinPoint.proceed();
            logger.info("{} completed in {} ms", method, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable ex) {
            logger.error("{} failed after {} ms — {}", method,
                    System.currentTimeMillis() - start, ex.getMessage(), ex);
            throw ex;
        }
    }

    private String safeToString(Object arg) {
        if (arg == null) return "null";
        if (arg instanceof HttpServletRequest) return "HttpServletRequest";
        if (arg instanceof HttpServletResponse) return "HttpServletResponse";
        return arg.toString();
    }
}
