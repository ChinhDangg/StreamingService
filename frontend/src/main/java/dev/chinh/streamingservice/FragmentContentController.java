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
    public Map<String, Object> getVideoPlayerContent() {
        Context context = new Context();

        String templateName = "video-player/video-player";

        Map<String, String> fragmentMap = new HashMap<>();
        fragmentMap.put("style", "video-player-style");
        fragmentMap.put("html", "video-player-html");

        var content = getCombinedFragmentAsResponse(context, templateName, fragmentMap);
        content.put("script", new String[] {
                "/static/js/video-player/video-player.js"
        });
        return content;
    }

    @GetMapping("/video")
    public Map<String, Object> getVideoContent() {
        Context context = new Context();
        context.setVariable("mainVideoContainerId", GlobalModelAttributes.mainVideoContainerId());

        String templateName = "video/video-page";

        Map<String, String> fragmentMap = new HashMap<>();
        fragmentMap.put("html", "video-page-html");

        var content = getCombinedFragmentAsResponse(context, templateName, fragmentMap);
        content.put("script", new String[] {
                "/static/js/video-player/video-player.js",
                "/static/js/video/video-page.js"
        });
        var videoPlayerContent = getVideoPlayerContent();
        content.put("style", videoPlayerContent.get("style"));
        return content;
    }

    @GetMapping("/album")
    public Map<String, Object> getAlbumContent() {
        Context context = new Context();
        context.setVariable("mainAlbumContainerId", GlobalModelAttributes.mainAlbumContainerId());

        String templateName = "album/album-page";

        Map<String, String> fragmentMap = new HashMap<>();
        fragmentMap.put("style", "album-style");
        fragmentMap.put("html", "album-html");

        var content = getCombinedFragmentAsResponse(context, templateName, fragmentMap);
        content.put("script", new String[] {
                "/static/js/album/album-page.js"
        });
        return content;
    }

    private Map<String, Object> getCombinedFragmentAsResponse(Context context, String templateName,
                                                              Map<String, String> fragmentMap) {
        Map<String, Object> response = new HashMap<>();

        for (Map.Entry<String, String> entry : fragmentMap.entrySet()) {
            Set<String> selectors = Collections.singleton(entry.getValue());
            String fragmentHtml = templateEngine.process(templateName, selectors, context);
            response.put(entry.getKey(), fragmentHtml);
        }

        if (response.get("style") != null) {
            String styles = response.get("style").toString();
            if (styles.startsWith("<style>")) styles = styles.substring(7);
            if (styles.endsWith("</style>")) styles = styles.substring(0, styles.length() - 8);
            response.put("style", styles);
        }

        return response;
    }
}
