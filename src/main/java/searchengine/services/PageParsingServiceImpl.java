package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.IndexEntity;
import searchengine.entity.LemmaEntity;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.services.interfaces.PageParsingService;
import searchengine.utility.ProjectParameters;

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

    @Override
    public void parsePage(String pageUrl, SiteEntity siteEntity){
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        try {
            log.info("Активные потоки: " + forkJoinPool.getActiveThreadCount());
            log.info("Запланированные задачи: " + forkJoinPool.getQueuedTaskCount());
            forkJoinPool.invoke(new PageTask(pageUrl, siteEntity, this, projectParameters));
            log.info("Все задачи завершены.");
            saveAllLemmas();
            saveAllIndexes();
        } finally {
            forkJoinPool.shutdown();
            log.info("ForkJoinPool завершён.");
        }
    }

    @Transactional
    public void saveAllData() {
        saveAllLemmas();
        saveAllIndexes();
    }

    public void savePageEntity(PageEntity pageEntity){
        pageRepository.save(pageEntity);
    }

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

    private void saveAllIndexes(){
        ConcurrentHashMap<PageEntity, Set<IndexEntity>> allIndexesByPage = new ConcurrentHashMap<>(PageTask.getAllIndexesByPage());
        for (Map.Entry<PageEntity, Set<IndexEntity>> pageEntitySetEntry : allIndexesByPage.entrySet()) {
            Set<IndexEntity> tempIndexEntry = pageEntitySetEntry.getValue();
            indexRepository.saveAll(tempIndexEntry);
        }
    }
}
