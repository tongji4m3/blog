package org.simpleframework.inject;

import com.tongji.controller.MyController;
import org.junit.jupiter.api.Test;
import org.simpleframework.core.BeanContainer;

public class DependencyInjectorTest {
    @Test
    public void doIocTest() {
        BeanContainer beanContainer = BeanContainer.getInstance();
        beanContainer.loadBeans("com.tongji");
        MyController myController = (MyController) beanContainer.getBean(MyController.class);
        System.out.println(myController);
        DependencyInjector dependencyInjector = new DependencyInjector(beanContainer);
        dependencyInjector.doIoc();
        System.out.println(myController);
    }
}
