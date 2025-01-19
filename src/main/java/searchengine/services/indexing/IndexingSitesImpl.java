package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.response.ApiResponse;
import searchengine.entity.SiteEntity;
import searchengine.entity.Status;
import searchengine.repository.SiteRepository;
import searchengine.services.indexing.interfaces.IndexingSitesService;
import searchengine.services.indexing.interfaces.PageProcessService;
import searchengine.services.indexing.interfaces.SiteProcessService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingSitesImpl implements IndexingSitesService {


    private final AtomicBoolean isIndexingStart = new AtomicBoolean(false);
    private final SiteRepository siteRepository;
    private final SitesList sitesList;
    private final PageProcessService pageProcessService;
    private final ForkJoinPool forkJoinPool = new ForkJoinPool();

    @Override
    public ResponseEntity<ApiResponse> startIndexing() {
        if (!isIndexingStart.compareAndSet(false, true)) {
            return ResponseEntity.ok(new ApiResponse(false, "Индексация уже запущена"));
        }

        log.info("Индексация начата");
        pageProcessService.deleteAllSiteAndPages();

        forkJoinPool.submit(() -> {
            try {
                List<SiteEntity> sites = initializeSites();
                forkJoinPool.invoke(new SiteIndexingTask(sites, pageProcessService, siteRepository));
            } catch (Exception e) {
                log.error("Ошибка во время индексации: ", e);
            } finally {
                isIndexingStart.set(false);
                log.info("Индексация завершена");
            }
        });

        return ResponseEntity.ok(new ApiResponse(true, "Индексация запущена"));
    }

    @Override
    public ResponseEntity<ApiResponse> indexPage(String path) {
        log.info("Индексация отдельной страницы: {}", path);
        try {
            pageProcessService.indexSinglePage(path);
        } catch (Exception e) {
            log.error("Ошибка индексации страницы {}: {}", path, e.getMessage(), e);
            return ResponseEntity.ok(new ApiResponse(false, "Ошибка индексации страницы"));
        }
        return ResponseEntity.ok(new ApiResponse(true, "Индексация страницы выполнена"));
    }

    @Override
    public ResponseEntity<ApiResponse> stopIndexing() {
        if (!isIndexingStart.compareAndSet(true, false)) {
            return ResponseEntity.ok(new ApiResponse(false, "Индексация не запущена"));
        }
        forkJoinPool.shutdownNow();
        log.info("Индексация остановлена");
        return ResponseEntity.ok(new ApiResponse(true, "Индексация остановлена"));
    }

    @Transactional
    private List<SiteEntity> initializeSites() {
        return sitesList.getSites().stream().map(site -> {
            SiteEntity siteEntity = new SiteEntity();
            siteEntity.setName(site.getName());
            siteEntity.setUrl(site.getUrl());
            siteEntity.setStatus(Status.INDEXING);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);
            return siteEntity;
        }).toList();
    }
}
