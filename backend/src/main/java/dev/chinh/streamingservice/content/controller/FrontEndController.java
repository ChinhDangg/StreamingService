package dev.chinh.streamingservice.content.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/page")
public class FrontEndController {

    @GetMapping("/test")
    public String index(){
        return "test";
    }
}
