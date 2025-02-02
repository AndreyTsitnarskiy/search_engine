package searchengine.services.indexing.managers;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VisitedUrlsManager {

    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();

    public boolean isUrlVisited(String url) {
        return !visitedUrls.add(url);
    }

    public void clearUrls(String siteUrl) {
        visitedUrls.removeIf(url -> url.startsWith(siteUrl));
    }
}
