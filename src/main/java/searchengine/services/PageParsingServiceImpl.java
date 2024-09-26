package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import searchengine.entity.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.interfaces.PageParsingService;
import searchengine.utility.ProjectParameters;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageParsingServiceImpl implements PageParsingService {

    private static final Logger businessLogger = LoggerFactory.getLogger("BUSINESS_LOGGER");

    private final PageRepository pageRepository;
    private final ProjectParameters projectParameters;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SiteRepository siteRepository;
    //private static final ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());

    @Override
    public void parsePage(String pageUrl, SiteEntity siteEntity){
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        long startTime = System.currentTimeMillis();
        try {
            forkJoinPool.invoke(new PageTask(pageUrl, siteEntity, this, projectParameters));
            saveAllLemmas();
            saveAllIndexes();
            updateSiteStatusIndexed(siteEntity, Statuses.INDEXED);
        } finally {
            forkJoinPool.shutdown();
            long endTime = System.currentTimeMillis();
            businessLogger.info("ForkJoinPool для {} {} завершён. Время выполнения: {} мс", siteEntity.getId(), siteEntity.getName(),  (endTime - startTime));
        }
    }

    public void savePageEntity(PageEntity pageEntity){
        if (pageEntity.getContent() == null || pageEntity.getContent().isEmpty()) {
            pageEntity.setContent("Error get content");
        }
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

    public void updateSiteStatusIndexed(SiteEntity siteEntity, Statuses status) {
        if(siteEntity.getStatuses() != Statuses.FAILED) {
            siteEntity.setStatuses(status);
            siteEntity.setLocalDateTime(LocalDateTime.now());
            siteRepository.save(siteEntity);
        }
    }

    //===================================SAVE ENTITY====================================================
    public void saveOneFrequencyLemmaEntity(LemmaEntity lemmaEntity){
        lemmaRepository.save(lemmaEntity);
    }


    private void saveAllIndexes(){
        long startTime = System.currentTimeMillis();
        Map<SiteEntity, List<IndexEntity>> allIndexesBySite = PageTask.getAllIndexEntityByPage();

        for (Map.Entry<SiteEntity, List<IndexEntity>> indexEntry : allIndexesBySite.entrySet()) {
            SiteEntity siteEntity = indexEntry.getKey();
            List<IndexEntity> indexList = indexEntry.getValue();

            if (!indexList.isEmpty()) {
                int batchSize = 1000;  // Можно регулировать этот размер
                for (int i = 0; i < indexList.size(); i += batchSize) {
                    List<IndexEntity> batch = indexList.subList(i, Math.min(i + batchSize, indexList.size()));
                    indexRepository.saveAll(batch);  // Сохранение пачками
                }
                businessLogger.info("Сохранены индексы для сайта: " + siteEntity.getId() + " " + siteEntity.getUrl());
            }
        }
        long endTime = System.currentTimeMillis();
        businessLogger.info("ForkJoinPool завершён. Время выполнения: {} мс", (endTime - startTime) + " method saveAllIndexes");
        businessLogger.info("Все индексы сохранены.");
    }

    private void saveAllLemmas() {
        long startTime = System.currentTimeMillis();
        ConcurrentHashMap<SiteEntity, ConcurrentHashMap<String, LemmaEntity>> allLemmasBySiteIdCopy = new ConcurrentHashMap<>(PageTask.getAllLemmasBySiteId());

        for (Map.Entry<SiteEntity, ConcurrentHashMap<String, LemmaEntity>> siteEntry : allLemmasBySiteIdCopy.entrySet()) {
            SiteEntity siteEntity = siteEntry.getKey();
            ConcurrentHashMap<String, LemmaEntity> siteLemmas = siteEntry.getValue();

            if (!siteLemmas.isEmpty()) {
                List<LemmaEntity> lemmaBatch = new ArrayList<>(siteLemmas.values());
                int batchSize = 1000;
                for (int i = 0; i < lemmaBatch.size(); i += batchSize){
                    List<LemmaEntity> batch = lemmaBatch.subList(i, Math.min(i + batchSize, lemmaBatch.size()));
                    lemmaRepository.saveAll(batch);
                }
                businessLogger.info("Сохранены леммы для сайта: " + siteEntity.getId() + " " + siteEntity.getUrl());
            }
        }
        long endTime = System.currentTimeMillis();
        businessLogger.info("ForkJoinPool завершён. Время выполнения: {} мс", (endTime - startTime) + "method saveAllLemmas");
        businessLogger.info("Все леммы сохранены.");
    }
}
