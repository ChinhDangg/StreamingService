package dev.chinh.streamingservice.frontend;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;

@ControllerAdvice
public class GlobalModelAttributes {

    @ModelAttribute("mainVideoContainerId")
    public static String mainVideoContainerId() {
        return "main-video-container";
    }

    @ModelAttribute("mainAlbumContainerId")
    public static String mainAlbumContainerId() {
        return "main-album-container";
    }

    @ModelAttribute("mainGrouperContainerId")
    public static String mainGrouperContainerId() {
        return "main-grouper-container";
    }

    @ModelAttribute("modifyAllow")
    public boolean modifyAllow(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream().anyMatch(o -> o.getAuthority().equals("ADMIN"));
    }
}
