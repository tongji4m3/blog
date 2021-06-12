package com.tongji.service;

import org.simpleframework.core.annotation.Service;

@Service
public class MyService {
    private int value;
    public MyService() {
        System.out.println("MyService init");
    }
}
