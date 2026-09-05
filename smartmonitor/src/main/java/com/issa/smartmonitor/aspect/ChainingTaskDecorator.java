package com.issa.smartmonitor.aspect;

import org.springframework.core.task.TaskDecorator;

public class ChainingTaskDecorator implements TaskDecorator {
    private final TaskDecorator first;
    private final TaskDecorator second;

    public ChainingTaskDecorator(TaskDecorator first, TaskDecorator second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public Runnable decorate(Runnable runnable) {
        return first.decorate(second.decorate(runnable));
    }
}