package searchengine.services.managers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ForkJoinPoolManager {

    private ForkJoinPool forkJoinPool = new ForkJoinPool();
    private volatile boolean isStopIndexing = false;

    public void executeTasks(Collection<? extends ForkJoinTask<?>> tasks) {
        tasks.forEach(ForkJoinTask::fork);
        tasks.forEach(ForkJoinTask::join);
    }

    public void shutdown() {
        log.info("Плавная остановка ForkJoinPool...");
        forkJoinPool.shutdown();
        try {
            if (!forkJoinPool.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Принудительное завершение ForkJoinPool...");
                forkJoinPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Ошибка при остановке ForkJoinPool", e);
            Thread.currentThread().interrupt();
        }
    }

    public void shutdownNow() {
        log.warn("Принудительная остановка ForkJoinPool!");
        isStopIndexing = true; // Флаг для остановки выполнения задач
        forkJoinPool.shutdownNow();
    }

    public void restartIfNeeded() {
        if (forkJoinPool == null || forkJoinPool.isShutdown() || forkJoinPool.isTerminated()) {
            log.warn("ForkJoinPool уже завершен, перезапускаем новый...");
            forkJoinPool = new ForkJoinPool();
            isStopIndexing = false;
        }
    }

    public boolean isIndexingStopped() {
        return isStopIndexing;
    }
}

