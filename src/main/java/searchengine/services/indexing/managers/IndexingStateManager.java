package searchengine.services.indexing.managers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class IndexingStateManager {
    private final AtomicBoolean indexing = new AtomicBoolean(false);

    public boolean isIndexingManage() {
        return indexing.get();
    }

    public void startIndexingManage() {
        indexing.set(true);
    }

    public void stopIndexingManage() {
        indexing.set(false);
    }
}
