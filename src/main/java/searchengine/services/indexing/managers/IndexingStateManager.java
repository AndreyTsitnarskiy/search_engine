package searchengine.services.indexing.managers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class IndexingStateManager {
    private final AtomicBoolean indexing = new AtomicBoolean(false);

    public boolean isIndexing() {
        return indexing.get();
    }

    public void startIndexing() {
        indexing.set(true);
    }

    public void stopIndexing() {
        indexing.set(false);
    }
}
