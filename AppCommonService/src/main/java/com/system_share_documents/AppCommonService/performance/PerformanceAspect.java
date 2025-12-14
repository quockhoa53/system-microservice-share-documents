package com.system_share_documents.AppCommonService.performance;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class PerformanceAspect {

    /**
     * Log time cho toàn bộ Controller, Service, Repository
     */
    @Around(
            "execution(* com.system_share_documents..controller..*(..)) || " +
                    "execution(* com.system_share_documents..service..*(..)) || " +
                    "execution(* com.system_share_documents..repository..*(..))"
    )
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        Object result = joinPoint.proceed();

        long time = System.currentTimeMillis() - start;

        if (time > 100) { // chỉ log nếu >100ms để tránh noisy
            log.warn("[PERF] {}.{}() took {} ms",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    time
            );
        } else {
            log.debug("[PERF] {}.{}() took {} ms",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    time
            );
        }

        return result;
    }
}

