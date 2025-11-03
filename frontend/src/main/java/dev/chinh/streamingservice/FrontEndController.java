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

    @GetMapping("/video")
    public String videoPage() {
        return "video-player/video-page";
    }

    @GetMapping("/album")
    public String albumPage() {
        return "video-player/album-page";
    }
}
