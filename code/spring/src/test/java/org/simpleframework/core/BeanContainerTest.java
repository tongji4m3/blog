package org.simpleframework.core;


import com.tongji.controller.MyController;
import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BeanContainerTest {
    private static BeanContainer beanContainer;

    @BeforeAll
    static void init() {
        beanContainer = BeanContainer.getInstance();
    }

    @Order(1)
    @Test
    public void loadBeansTest() {
        Assertions.assertEquals(false, beanContainer.isLoaded());
        beanContainer.loadBeans("com.tongji.controller");
        Assertions.assertEquals(1,beanContainer.size());
    }

    @Order(2)
    @Test
    public void getBeanTest() {
        MyController myController = (MyController)beanContainer.getBean(MyController.class);
        System.out.println(myController);
        Assertions.assertEquals(true, myController instanceof MyController);
    }
}
