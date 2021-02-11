package com.helltab.hand_spring.web.service;


import com.helltab.hand_spring.annotation.H_Service;

@H_Service
public class DemoService implements IService {
    @Override
    public String getName() {
        return "demo service";
    }
}
