package dev.chinh.streamingservice;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/page")
public class FrontEndController {

    @GetMapping("/search")
    public String searchPage(){
        return "video-player/search-page";
    }

    @GetMapping("/video")
    public String videoPage() {
        return "video-player/video-page";
    }

    @GetMapping("/album")
    public String albumPage() {
        return "video-player/album-page";
    }

    @GetMapping("/search/{extra}")
    public String searchPage2(@PathVariable String extra) {
        return "video-player/search-page";
    }
}
