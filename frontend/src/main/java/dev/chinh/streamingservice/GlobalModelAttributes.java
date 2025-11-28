package dev.chinh.streamingservice;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;

@ControllerAdvice
public class GlobalModelAttributes {

    @ModelAttribute("mainVideoContainerId")
    public static String mainVideoContainerId() {
        return "main-video-container";
    }

    @ModelAttribute("modifyAllow")
    public boolean modifyAllow(Principal principal) {
        return true;
    }
}
