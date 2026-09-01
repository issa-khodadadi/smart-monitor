package com.issa.smartmonitor.aspect;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

public class MonitoringTaskDecoratorBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof ThreadPoolTaskExecutor executor) {
            TaskDecorator existing = readExistingDecorator(executor);
            if (existing == null) {
                executor.setTaskDecorator(new MonitoringTaskDecorator());
            } else if (!(existing instanceof MonitoringTaskDecorator)) {
                executor.setTaskDecorator(new ChainingTaskDecorator(new MonitoringTaskDecorator(), existing));
            }
        }
        return bean;
    }

    private TaskDecorator readExistingDecorator(ThreadPoolTaskExecutor executor) {
        try {
            Field field = ReflectionUtils.findField(executor.getClass(), "taskDecorator");
            if (field == null) return null;
            ReflectionUtils.makeAccessible(field);
            return (TaskDecorator) ReflectionUtils.getField(field, executor);
        } catch (Exception e) {
            return null;
        }
    }
}