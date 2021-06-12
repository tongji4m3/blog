package org.simpleframework.inject;

import lombok.extern.slf4j.Slf4j;
import org.simpleframework.core.BeanContainer;
import org.simpleframework.inject.annotation.Autowired;
import org.simpleframework.util.ClassUtil;
import org.simpleframework.util.ValidationUtil;

import java.lang.reflect.Field;
import java.util.Set;

@Slf4j
public class DependencyInjector {
    private BeanContainer beanContainer;

    public DependencyInjector() {
        beanContainer = BeanContainer.getInstance();
    }

    public DependencyInjector(BeanContainer beanContainer) {
        this.beanContainer = beanContainer;
    }

    public void doIoc() {
        if (ValidationUtil.isEmpty(beanContainer.getClasses())) {
            log.warn("empty class in beanContainer");
            return;
        }
        for (Class<?> clazz : beanContainer.getClasses()) {
            Field[] fields = clazz.getDeclaredFields();
            if (fields == null || fields.length == 0) {
                continue;
            }
            for (Field field : fields) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    Autowired autowired = field.getAnnotation(Autowired.class);
                    String autowiredValue = autowired.value();
                    Class<?> fieldClass = field.getType();
                    Object fieldInstance = getFieldInstance(fieldClass, autowiredValue);
                    if (fieldInstance == null) {
                        throw new RuntimeException();
                    } else {
                        Object targetBean = beanContainer.getBean(clazz);
                        // 通过反射注入成员变量
                        ClassUtil.setField(field, targetBean, fieldInstance, true);
                    }
                }
            }
        }
    }

    /**
     * 根据Class在beanContainer中获取其实例或实现类
     */
    private Object getFieldInstance(Class<?> fieldClass, String autowiredValue) {
        Object fieldValue = beanContainer.getBean(fieldClass);
        if (fieldValue != null) {
            return fieldValue;
        }
        // 为空代表注入的是接口，我们需要找到他的实现类
        Class<?> implementClass = getImplementClass(fieldClass, autowiredValue);
        if (implementClass != null) {
            return beanContainer.getBean(implementClass);
        }
        return null;
    }

    private Class<?> getImplementClass(Class<?> fieldClass, String autowiredValue) {
        Set<Class<?>> classSet = beanContainer.getClassesBySuper(fieldClass);
        if (ValidationUtil.isEmpty(classSet)) {
            if (autowiredValue == null || autowiredValue.equals("")) {
                if (classSet.size() == 1) {
                    return classSet.iterator().next();
                } else {
                    // 大于一个实现类，并且用户未指定
                    throw new RuntimeException("multiple implement class");
                }
            } else {
                for (Class<?> clazz : classSet) {
                    if (autowiredValue.equals(clazz.getSimpleName())) {
                        return clazz;
                    }
                }
            }
        }
        return null;
    }
}
