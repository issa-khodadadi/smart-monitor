package com.issa.smartmonitor.aspect;

import org.springframework.core.task.TaskDecorator;

public class MonitoringTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        MonitoringContext ctx = MonitoringContext.capture();
        return ctx.wrap(runnable);
    }
}