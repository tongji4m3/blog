package com.tongji.controller;

import com.tongji.service.MyService;
import lombok.ToString;
import org.simpleframework.core.annotation.Controller;
import org.simpleframework.inject.annotation.Autowired;

@Controller
@ToString
public class MyController {
    @Autowired
    private MyService myService;

    public MyController() {
        System.out.println("MyController init");
    }
}
