package dev.chinh.streamingservice;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/page/frag")
public class FragmentContentController {

    private final TemplateEngine templateEngine;

    @GetMapping("/video-player")
    public Map<String, Object> getCombinedContent() {
        Context context = new Context();

        String templateName = "video-player/video-player";

        Map<String, String> fragmentMap = new HashMap<>();
        fragmentMap.put("style", "video-player-style");
        fragmentMap.put("html", "video-player-html");
        fragmentMap.put("script", "video-player-script");

        return getCombinedFragmentAsResponse(context, templateName, fragmentMap);
    }

    @GetMapping("/video")
    public Map<String, Object> getVideoContent() {
        Context context = new Context();

        String templateName = "video-player/video-page";

        Map<String, String> fragmentMap = new HashMap<>();
        fragmentMap.put("style", "video-page-style");
        fragmentMap.put("html", "video-page-html");
        fragmentMap.put("script", "video-page-script");

        return getCombinedFragmentAsResponse(context, templateName, fragmentMap);
    }

    @GetMapping("/album")
    public Map<String, Object> getAlbumContent() {
        Context context = new Context();

        String templateName = "video-player/album-page";

        Map<String, String> fragmentMap = new HashMap<>();
        fragmentMap.put("style", "album-style");
        fragmentMap.put("html", "album-html");
        fragmentMap.put("script", "album-script");

        return getCombinedFragmentAsResponse(context, templateName, fragmentMap);
    }

    private Map<String, Object> getCombinedFragmentAsResponse(Context context, String templateName,
                                                              Map<String, String> fragmentMap) {
        Map<String, Object> response = new HashMap<>();

        for (Map.Entry<String, String> entry : fragmentMap.entrySet()) {
            Set<String> selectors = Collections.singleton(entry.getValue());
            String fragmentHtml = templateEngine.process(templateName, selectors, context);
            response.put(entry.getKey(), fragmentHtml);
            System.out.println(fragmentHtml);
        }

        if (response.get("script") != null) {
            String scripts = response.get("script").toString();
            String[] parts = scripts.split("\n");
            String[] scriptOnly = Arrays.stream(parts)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toArray(String[]::new);
            response.put("script", scriptOnly);
        }

        return response;
    }
}
