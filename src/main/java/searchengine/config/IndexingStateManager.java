package searchengine.config;

import org.springframework.stereotype.Component;

@Component
public class IndexingStateManager {
    private volatile boolean isIndexing = false;

    public synchronized boolean startIndexing() {
        if (isIndexing) {
            return false;
        }
        isIndexing = true;
        return true;
    }

    public synchronized void stopIndexing() {
        isIndexing = false;
    }

    public boolean isIndexing() {
        return isIndexing;
    }
}
