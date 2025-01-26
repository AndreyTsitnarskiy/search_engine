package searchengine.services.indexing;

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

    public boolean isProcessingCompleted(String siteUrl) {
        return visitedUrls.stream().noneMatch(url -> url.startsWith(siteUrl));
    }
}
