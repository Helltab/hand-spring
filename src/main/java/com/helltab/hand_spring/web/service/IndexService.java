package com.helltab.hand_spring.web.service;


import com.helltab.hand_spring.annotation.H_Service;

@H_Service
public class IndexService implements IService {
    @Override
    public String getName() {
        return "index service";
    }
}
