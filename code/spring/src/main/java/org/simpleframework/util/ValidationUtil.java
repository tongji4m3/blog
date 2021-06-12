package org.simpleframework.util;

import org.simpleframework.core.BeanContainer;

import java.util.Collection;
import java.util.Collections;

public class ValidationUtil {
    public static boolean isEmpty(Collection<?> obj) {
        return obj == null || obj.isEmpty();
    }
}
