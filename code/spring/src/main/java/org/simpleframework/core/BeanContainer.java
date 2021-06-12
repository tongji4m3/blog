package org.simpleframework.core;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.simpleframework.core.annotation.Component;
import org.simpleframework.core.annotation.Controller;
import org.simpleframework.core.annotation.Repository;
import org.simpleframework.core.annotation.Service;
import org.simpleframework.util.ClassUtil;
import org.simpleframework.util.ValidationUtil;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BeanContainer {
    // 存储了Class,Class对应的对象
    private final Map<Class<?>, Object> beanMap = new ConcurrentHashMap<>();
    // 加载bean的注解列表。被这些注解标记的类就会被BeanContainer管理起来
    private static final List<Class<? extends Annotation>> BEAN_ANNOTATION = Arrays.asList(Component.class, Controller.class, Service.class, Repository.class);

    private boolean loaded = false;

    public boolean isLoaded() {
        return loaded;
    }

    public static BeanContainer getInstance() {
        return ContainerHolder.HOLDER.instance;
    }

    private enum ContainerHolder {
        HOLDER;
        private final BeanContainer instance;

        ContainerHolder() {
            instance = new BeanContainer();
        }
    }

    public int size() {
        return beanMap.size();
    }

    // 扫描加载所有packageName下的bean
    public synchronized void loadBeans(String packageName) {
        if (isLoaded()) {
            log.warn("BeanContainer has been loaded");
            return;
        }

        Set<Class<?>> classSet = ClassUtil.extractPackageClass(packageName);
        if (ValidationUtil.isEmpty(classSet)) {
            log.warn("extract nothing from packageName" + packageName);
            return;
        }
        for (Class<?> clazz : classSet) {
            for (Class<? extends Annotation> annotation : BEAN_ANNOTATION) {
                if (clazz.isAnnotationPresent(annotation)) {
                    beanMap.put(clazz, ClassUtil.newInstance(clazz, true));
                }
            }
        }
        loaded = true;
    }

    public Object addBean(Class<?> clazz, Object bean) {
        return beanMap.put(clazz, bean);
    }

    public Object removeBean(Class<?> clazz) {
        return beanMap.remove(clazz);
    }

    public Object getBean(Class<?> clazz) {
        return beanMap.get(clazz);
    }

    public Set<Class<?>> getClasses() {
        return beanMap.keySet();
    }

    public Set<Object> getBeans() {
        return new HashSet<>(beanMap.values());
    }

    public Set<Class<?>> getClassesByAnnotation(Class<? extends Annotation> annotation) {
        Set<Class<?>> keySet = beanMap.keySet();
        if (ValidationUtil.isEmpty(keySet)) {
            log.warn("nothing in beanMap");
            return null;
        }
        Set<Class<?>> classSet = new HashSet<>();
        for (Class<?> clazz : keySet) {
            if (clazz.isAnnotationPresent(annotation)) {
                classSet.add(clazz);
            }
        }
        return classSet.size() > 0 ? classSet : null;
    }

    public Set<Class<?>> getClassesBySuper(Class<?> interfaceOrClass) {
        Set<Class<?>> keySet = beanMap.keySet();
        if (ValidationUtil.isEmpty(keySet)) {
            log.warn("nothing in beanMap");
            return null;
        }
        Set<Class<?>> classSet = new HashSet<>();
        for (Class<?> clazz : keySet) {
            if (interfaceOrClass.isAssignableFrom(clazz) && !clazz.equals(interfaceOrClass)) {
                classSet.add(clazz);
            }
        }
        return classSet.size() > 0 ? classSet : null;
    }
}
