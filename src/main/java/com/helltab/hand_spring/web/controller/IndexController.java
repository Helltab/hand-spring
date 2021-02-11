package com.helltab.hand_spring.web.controller;

import com.helltab.hand_spring.annotation.H_Autowired;
import com.helltab.hand_spring.annotation.H_Controller;
import com.helltab.hand_spring.annotation.H_RequestMapping;
import com.helltab.hand_spring.web.service.IService;

@H_Controller
@H_RequestMapping("index")
public class IndexController {
    @H_Autowired("indexService")
    IService iService;

    @H_RequestMapping("/getName")
    public String getName() {
        return iService.getName();
    }

}
