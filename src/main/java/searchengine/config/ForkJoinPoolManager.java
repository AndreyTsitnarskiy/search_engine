package searchengine.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ForkJoinPoolManager {

    private final ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();

    public void executeTasks(List<? extends ForkJoinTask<?>> tasks) {
        log.info("Поток: {} - Добавляем {} задач в ForkJoinPool", Thread.currentThread().getName(), tasks.size());
        tasks.forEach(task -> {
            log.info("Поток: {} - Отправка задачи в пул: {}", Thread.currentThread().getName(), task);
            task.fork();
        });

        tasks.forEach(task -> {
            log.info("Поток: {} - Ожидание завершения задачи: {}", Thread.currentThread().getName(), task);
            task.join();
        });

        log.info("Поток: {} - Все задачи завершены", Thread.currentThread().getName());
    }

    public void shutdown() {
        log.info("Поток: {} - Завершение ForkJoinPool", Thread.currentThread().getName());
        forkJoinPool.shutdown();
        try {
            if (!forkJoinPool.awaitTermination(60, TimeUnit.SECONDS)) {
                log.warn("Поток: {} - Превышено время ожидания завершения ForkJoinPool, принудительное завершение", Thread.currentThread().getName());
                forkJoinPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Поток: {} - Ошибка при завершении ForkJoinPool: {}", Thread.currentThread().getName(), e.getMessage());
            forkJoinPool.shutdownNow();
        }
    }
}
