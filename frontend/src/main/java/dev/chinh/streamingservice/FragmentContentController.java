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
@RequestMapping("/page")
public class FragmentContentController {

    private final TemplateEngine templateEngine;

    @GetMapping("/video")
    public Map<String, Object> getCombinedContent() {

        Context context = new Context();

        // 1. Define the template file and the list of fragment names
        String templateName = "video-player/video-player";

        Map<String, String> fragmentMap = new HashMap<>();
        fragmentMap.put("style", "video-player-style");
        fragmentMap.put("html", "video-player-html");
        fragmentMap.put("script", "video-player-script");

        Map<String, Object> response = new HashMap<>();

        for (Map.Entry<String, String> entry : fragmentMap.entrySet()) {
            Set<String> selectors = Collections.singleton(entry.getValue());
            String fragmentHtml = templateEngine.process(templateName, selectors, context);
            response.put(entry.getKey(), fragmentHtml);
        }

        String scripts = response.get("script").toString();
        String[] parts = scripts.split("\n");
        String[] scriptOnly = Arrays.stream(parts)
                .skip(1)
                .limit(parts.length - 2)
                .map(String::trim)
                .toArray(String[]::new);
        response.put("script", scriptOnly);

        return response;
    }
}
