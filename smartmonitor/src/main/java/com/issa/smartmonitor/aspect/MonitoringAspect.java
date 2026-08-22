package com.issa.smartmonitor.aspect;

import com.issa.smartmonitor.core.MetricRegistry;
import com.issa.smartmonitor.enums.Layer;
import lombok.AllArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
@AllArgsConstructor
public class MonitoringAspect {
    private final MetricRegistry registry;

    @Around("(within(@org.springframework.stereotype.Service *) || " +
            "within(@org.springframework.stereotype.Repository *) || " +
            "within(@org.springframework.web.bind.annotation.RestController *) || " +
            "within(@org.springframework.stereotype.Controller *)) " +
            "&& !within(com.issa.smartmonitor..*)")
    public Object monitor(ProceedingJoinPoint pjp) throws Throwable {
        String className = pjp.getTarget().getClass().getSimpleName();
        String methodName = pjp.getSignature().getName();
        String layer = detectLayer(pjp.getTarget().getClass());
        String key = className + "#" + methodName;

        CallStack.Frame parent = CallStack.peekParent();
        String rootKeyBeforePush = CallStack.rootKey();
        boolean isRoot = rootKeyBeforePush == null;
        String endpointKey = isRoot ? key : rootKeyBeforePush;

        CallStack.Frame frame = CallStack.push(key);

        Runtime runtime = Runtime.getRuntime();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();
        long start = System.nanoTime();
        boolean isError = false;
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            isError = true;
            throw t;
        } finally {
            long duration = System.nanoTime() - start;
            long memAfter = runtime.totalMemory() - runtime.freeMemory();
            long memoryDelta = Math.max(memAfter - memBefore, 0);

            registry.record(className, methodName, layer, duration, isError, memoryDelta);

            long selfTime = duration - frame.childTimeNanos;
            registry.addSelfTime(key, selfTime);

            registry.recordToEndpoint(endpointKey, className, methodName, layer, duration, selfTime, isError, memoryDelta, isRoot);

            if ("REPOSITORY".equals(layer)) {
                if (parent != null) registry.addDbTimeToCaller(parent.key, duration);
                registry.addDbTimeToEndpoint(endpointKey, duration);
            }

            CallStack.pop();

            if (parent != null) {
                parent.childTimeNanos += duration;
                registry.recordEdge(parent.key, key, duration);
            }
        }
    }

    private String detectLayer(Class<?> clazz) {
        if (clazz.isAnnotationPresent(org.springframework.stereotype.Repository.class))
            return Layer.REPOSITORY.name();

        if (clazz.isAnnotationPresent(org.springframework.web.bind.annotation.RestController.class) ||
                clazz.isAnnotationPresent(org.springframework.stereotype.Controller.class))
            return Layer.CONTROLLER.name();

        if (clazz.isAnnotationPresent(org.springframework.stereotype.Service.class))
            return Layer.SERVICE.name();

        return Layer.OTHER.name();
    }
}