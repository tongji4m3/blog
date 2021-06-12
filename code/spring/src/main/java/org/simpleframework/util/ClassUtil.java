package org.simpleframework.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class ClassUtil {
    private static final String FILE_PROTOCOL = "file";

    /**
     * 为什么要这样：光是通过用户传入的包名，我们是没办法定位到具体路径的
     * <p>
     * 作用是加载packageName包下的所有类
     * 注意包下可能还有子包，所以会递归的搜索出所有的类
     * <p>
     * 例如：传入包名：org.simpleframework.core
     * 会找到包下类全路径类名：org.simpleframework.core.annotation.Component
     * 通过Class.forName(className)加载
     */
    public static Set<Class<?>> extractPackageClass(String packageName) {
        ClassLoader classLoader = getClassLoader();
        URL url = classLoader.getResource(packageName.replace(".", "/"));
        if (url == null) {
            log.warn("unable to retrieve anything from packaging:" + packageName);
            return null;
        }
        Set<Class<?>> classSet = null;
        // 过滤出文件类型的资源
        if (url.getProtocol().equalsIgnoreCase(FILE_PROTOCOL)) {
            classSet = new HashSet<>();
            File packageDirectory = new File(url.getPath());
            extractClassFile(classSet, packageDirectory, packageName);
        }
        return classSet;
    }

    private static void extractClassFile(Set<Class<?>> classSet, File fileSource, String packageName) {
        if (!fileSource.isDirectory()) return;
        // 过滤掉了非目录文件(.class文件直接加载)
        File[] files = fileSource.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                if (file.isDirectory()) {
                    return true;
                } else {
                    String absolutePath = file.getAbsolutePath();
                    if (absolutePath.endsWith(".class")) {
                        // 若是class文件，则直接加载
                        addToClassSet(absolutePath);
                    }
                }
                return false;
            }

            private void addToClassSet(String absolutePath) {
                // 从文件绝对值路径提取包含package的类名，并且通过反射机制获取对应的Class对象放入classSet中
                absolutePath = absolutePath.replace(File.separator, ".");
                String className = absolutePath.substring(absolutePath.indexOf(packageName));
                className = className.substring(0, className.lastIndexOf("."));
                Class<?> loadClass = loadClass(className);
                classSet.add(loadClass);
            }
        });
        // if语句是因为 如果对空的File数组进行for循环遍历会有异常
        if (files != null) {
            for (File file : files) {
                extractClassFile(classSet, file, packageName); // 继续处理子目录
            }
        }
    }

    public static ClassLoader getClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    public static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            log.error("load class error:", e);
            throw new RuntimeException();
        }
    }

    // 实例化class
    public static <T> T newInstance(Class<?> clazz, boolean accessible) {
        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(accessible);
            return (T) constructor.newInstance();

        } catch (Exception e) {
            log.error("newInstance error", e);
            throw new RuntimeException();
        }
    }

    public static void setField(Field field, Object target, Object value, boolean accessible) {
        field.setAccessible(accessible);
        try {
            field.set(target, value);
        } catch (IllegalAccessException e) {
            log.error("setField Error," + e);
            throw new RuntimeException();
        }
    }
}
