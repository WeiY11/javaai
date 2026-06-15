package com.example.evimind.audit;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** AOP 切面：拦截 @Auditable 注解的方法，自动记录审计日志。 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

  private final AuditService auditService;
  private final ExpressionParser parser = new SpelExpressionParser();
  private final ParameterNameDiscoverer paramNameDiscoverer = new DefaultParameterNameDiscoverer();

  @Around("@annotation(auditable)")
  public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
    // 先执行目标方法
    Object result = joinPoint.proceed();

    // 方法执行成功后记录审计日志
    try {
      Long resourceId = extractResourceId(joinPoint, auditable.resourceIdExpression());

      Map<String, Object> detail = new LinkedHashMap<>();
      detail.put("method", joinPoint.getSignature().toShortString());

      // 记录方法参数（限制大小，避免敏感信息泄露）
      MethodSignature sig = (MethodSignature) joinPoint.getSignature();
      String[] paramNames = sig.getParameterNames();
      Object[] args = joinPoint.getArgs();
      if (paramNames != null) {
        for (int i = 0; i < Math.min(paramNames.length, 5); i++) {
          String val = args[i] != null ? args[i].toString() : "null";
          if (val.length() > 200) val = val.substring(0, 200) + "...";
          detail.put("param." + paramNames[i], val);
        }
      }

      auditService.log(auditable.action(), auditable.resourceType(), resourceId, detail);
    } catch (Exception e) {
      log.warn("Audit aspect failed to log: {}", e.getMessage());
    }

    return result;
  }

  private Long extractResourceId(ProceedingJoinPoint joinPoint, String expression) {
    if (expression == null || expression.isBlank()) {
      // 尝试从第一个参数提取（如果它是 Long 类型）
      Object[] args = joinPoint.getArgs();
      if (args.length > 0 && args[0] instanceof Long) {
        return (Long) args[0];
      }
      return null;
    }

    try {
      MethodSignature sig = (MethodSignature) joinPoint.getSignature();
      Method method = sig.getMethod();
      String[] paramNames = paramNameDiscoverer.getParameterNames(method);

      EvaluationContext context = new StandardEvaluationContext();
      if (paramNames != null) {
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < paramNames.length; i++) {
          ((StandardEvaluationContext) context).setVariable(paramNames[i], args[i]);
        }
      }

      Object value = parser.parseExpression(expression).getValue(context);
      if (value instanceof Number) {
        return ((Number) value).longValue();
      }
    } catch (Exception e) {
      log.debug(
          "Could not extract resource ID from expression '{}': {}", expression, e.getMessage());
    }
    return null;
  }
}
