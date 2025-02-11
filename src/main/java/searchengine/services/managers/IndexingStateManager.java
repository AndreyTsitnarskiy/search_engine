package searchengine.services.managers;

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

    public boolean startIndexingManage() {
        return indexing.compareAndSet(false, true);
    }

    public boolean stopIndexingManage() {
        return indexing.compareAndSet(true, false);
    }
}
