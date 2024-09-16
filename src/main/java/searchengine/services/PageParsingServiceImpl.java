package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.entity.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.interfaces.PageParsingService;
import searchengine.utility.ProjectParameters;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageParsingServiceImpl implements PageParsingService {

    private final PageRepository pageRepository;
    private final ProjectParameters projectParameters;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SiteRepository siteRepository;

    @Override
    public void parsePage(String pageUrl, SiteEntity siteEntity){
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        try {
            log.info("Активные потоки: " + forkJoinPool.getActiveThreadCount());
            log.info("Запланированные задачи: " + forkJoinPool.getQueuedTaskCount());
            forkJoinPool.invoke(new PageTask(pageUrl, siteEntity, this, projectParameters));
            log.info("Все задачи завершены.");
            saveAllLemmas();
        } finally {
            forkJoinPool.shutdown();
            log.info("ForkJoinPool завершён.");
        }
    }

    public void savePageEntity(PageEntity pageEntity){
        pageRepository.save(pageEntity);
    }

    //===================================UPDATE STATUS====================================================
    public void updateSiteStatusFailed(SiteEntity siteEntity, Statuses status, String error) {
        siteEntity.setStatuses(status);
        siteEntity.setLocalDateTime(LocalDateTime.now());
        siteEntity.setLastError(error);
        siteRepository.save(siteEntity);
    }

    public void updateSiteStatusIndexing(SiteEntity siteEntity, Statuses status) {
        siteEntity.setStatuses(status);
        siteEntity.setLocalDateTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
    }

    //===================================SAVE ENTITY====================================================
    private void saveAllLemmas() {
        ConcurrentHashMap<SiteEntity, ConcurrentHashMap<String, LemmaEntity>> allLemmasBySiteIdCopy = new ConcurrentHashMap<>(PageTask.getAllLemmasBySiteId());

        for (Map.Entry<SiteEntity, ConcurrentHashMap<String, LemmaEntity>> siteEntry : allLemmasBySiteIdCopy.entrySet()) {
            SiteEntity siteEntity = siteEntry.getKey();
            ConcurrentHashMap<String, LemmaEntity> siteLemmas = siteEntry.getValue();

            if (!siteLemmas.isEmpty()) {
                lemmaRepository.saveAll(siteLemmas.values());
                log.info("Сохранены леммы для сайта: " + siteEntity.getId() + " " + siteEntity.getUrl());
            }
        }
        log.info("Все леммы сохранены.");
    }

    public void saveAllIndexes(Set<IndexEntity> setIndexes){
        indexRepository.saveAll(setIndexes);
    }
}
