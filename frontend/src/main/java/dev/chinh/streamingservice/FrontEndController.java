package dev.chinh.streamingservice;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/page")
public class FrontEndController {

    @GetMapping("/search")
    public String searchPage(){
        return "video-player/search-result-test2";
    }
}
