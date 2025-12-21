package dev.chinh.streamingservice.frontend;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/page")
public class PageController {

    @GetMapping("/search")
    public String searchPage(){
        return "search/search-page";
    }

    @GetMapping("/search/{extra}")
    public String searchPage2(@PathVariable String extra) {
        return "search/search-page";
    }

    @GetMapping("/browse")
    public String browsePage() {
        return "browse/browse-page";
    }

    @GetMapping("/browse/{extra}")
    public String browsePage2(@PathVariable String extra) {
        return "browse/browse-page";
    }

    @GetMapping("/video")
    public String videoPage() {
        return "video/video-page";
    }

    @GetMapping("/album")
    public String albumPage() {
        return "album/album-page";
    }

    @GetMapping("/album-grouper")
    public String albumGrouperPage(Model model) {
        model.addAttribute("mainGrouperContainerId", GlobalModelAttributes.mainGrouperContainerId());
        return "album-grouper/album-grouper-page";
    }


    @GetMapping("/modify/name")
    public String modifyNamePage() {
        return "upload/modify-name-page";
    }

    @GetMapping("/upload/media")
    public String uploadMediaPage() {
        return "upload/media-upload-page";
    }


    @GetMapping("/login")
    public String loginPage(){
        return "login/login-page";
    }
}
