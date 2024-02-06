package io.github.gonguasp.log.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gonguasp.log.annotation.Logging;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class LoggingAspect {

    @Autowired
    private HttpServletRequest request;

    private final ObjectMapper objectMapper;

    @Pointcut("@annotation(io.github.gonguasp.log.annotation.Logging)")
    public void logWithPointcut() {}

    @Pointcut("@within(io.github.gonguasp.log.annotation.Logging)")
    public void logClassWithAnnotationPointcut() {}

    @Around("logWithPointcut() || logClassWithAnnotationPointcut()")
    public Object logPublicMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        return addLogs(joinPoint, getAnnotation(joinPoint));
    }

    private Object addLogs(ProceedingJoinPoint joinPoint, Logging logging) throws Throwable {
        logRequest(joinPoint.getSignature().getDeclaringType(), logging);
        String method = getExecutedMethod(joinPoint);
        Object[] args = joinPoint.getArgs();
        Object[] originalArgs = Arrays.copyOf(args, args.length);
        log.atLevel(logging.level()).log(method + ": Executing with args " + getObjectsAsJson(args));
        return proceed(joinPoint, logging, method, originalArgs);
    }

    private Object proceed(ProceedingJoinPoint joinPoint, Logging logging, String method, Object[] originalArgs) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long end = System.currentTimeMillis();
        log.atLevel(logging.level()).log(method + ": Finished. Execution time " + (end - start) + "ms with result " + getObjectsAsJson(result));
        if (!Arrays.equals(originalArgs, joinPoint.getArgs())) {
            log.atLevel(logging.level()).log(method + ": Arguments have changed " + getObjectsAsJson(joinPoint.getArgs()));
        }
        return result;
    }

    private String getObjectsAsJson(Object... args) throws JsonProcessingException {
        return objectMapper.writeValueAsString(args);
    }

    private String getExecutedMethod(ProceedingJoinPoint joinPoint) {
        StringBuilder stringBuilder = new StringBuilder(joinPoint.getSignature().getDeclaringType().getSimpleName());
        stringBuilder.append(".");
        stringBuilder.append(((MethodSignature) joinPoint.getSignature()).getMethod().getName());
        stringBuilder.append(": ");
        return stringBuilder.toString();
    }

    private void logRequest(Class clazz, Logging logging) {
        if (clazz.isAnnotationPresent(Controller.class) || clazz.isAnnotationPresent(RestController.class)) {
            log.atLevel(logging.level()).log(getRequestDetails());
        }
    }

    private String getRequestDetails() {
        StringBuilder stringBuilder = new StringBuilder("Received request ");
        stringBuilder.append(request.getProtocol());
        stringBuilder.append(" ");
        stringBuilder.append(request.getMethod());
        stringBuilder.append(" ");
        stringBuilder.append(request.getRequestURI());
        return stringBuilder.toString();
    }

    private Logging getAnnotation(ProceedingJoinPoint joinPoint) {
        Logging logging = ((MethodSignature) joinPoint.getSignature()).getMethod().getAnnotation(Logging.class);
        if (logging == null) {
            logging = (Logging) joinPoint.getSignature().getDeclaringType().getDeclaredAnnotation(Logging.class);
        }

        return logging;
    }
}
