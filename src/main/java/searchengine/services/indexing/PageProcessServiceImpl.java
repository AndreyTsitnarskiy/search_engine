package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SitesList;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.entity.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.indexing.interfaces.LemmaProcessService;
import searchengine.services.indexing.interfaces.PageProcessService;
import searchengine.utility.PropertiesProject;
import searchengine.utility.UtilCheck;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageProcessServiceImpl implements PageProcessService {

    private final SitesList sitesList;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final PropertiesProject projectParameters;
    private final LemmaProcessService lemmaProcessService;
    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Boolean> siteErrorMap = new ConcurrentHashMap<>();
    private final Map<Integer, ForkJoinPool> sitePools = new ConcurrentHashMap<>();

    @Override
    public void parsePage(String pageUrl, Document document, SiteEntity siteEntity) {
        int poolSize = UtilCheck.calculateForkJoinPoolSize(sitesList.getSites().size());
        ForkJoinPool forkJoinPool = sitePools.computeIfAbsent(siteEntity.getId(),
                id -> new ForkJoinPool(poolSize));
        try {
            forkJoinPool.invoke(new SiteTask(pageUrl, siteEntity, projectParameters, this));
            if (isSiteProcessingCompleted(siteEntity)) {
                shutdownSiteForkJoinPool(siteEntity.getId());
                log.info("Завершены все потоки для сайта: " + siteEntity.getUrl());
                batchProcessingLemmaAndIndex(siteEntity);
            }
        } catch (Exception e) {
            log.error("ERROR " + e.getMessage());
        }
    }

    @Transactional
    @Override
    public void batchProcessingLemmaAndIndex(SiteEntity siteEntity){
        log.info("ЗАПУЩЕН парсинг лем страниц сайта {}", siteEntity.getUrl());
        int totalPageCount = pageRepository.countPagesBySiteId(siteEntity.getId());
        int batchSize = 100;
        int offset = 0;
        if (totalPageCount != 0) {
            while (offset < totalPageCount) {
                List<PageEntity> pageBatch = pageRepository.findPagesBySiteIdWithPagination(siteEntity.getId(), batchSize, offset);
                lemmaProcessService.parsingAndSaveContent(siteEntity, pageBatch);
                offset += batchSize;
            }
        }
        log.info("ЗАВЕРШЕН парсинг лем страниц сайта {}", siteEntity.getUrl());
    }

    @Override
    public void initializeSite(int siteId) {
        siteErrorMap.put(siteId, false);
    }

    public boolean isUrlVisited(String url) {
        return !visitedUrls.add(url);
    }

    @Transactional
    public void processPage(String url, Document document, SiteEntity siteEntity) {
        try {
            PageEntity pageEntity = new PageEntity();
            pageEntity.setPath(url);
            pageEntity.setSite(siteEntity);
            pageEntity.setContent(document.html());
            pageEntity.setCode(200);
            pageRepository.save(pageEntity);
        } catch (DataIntegrityViolationException e) {
            log.error("Запись уже существует в базе данных для страницы " + siteEntity.getUrl() + url);
        }
    }

    @Transactional
    public void updateStatusPage(SiteEntity siteEntity, String url, int code) {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(url);
        pageEntity.setCode(code);
        pageEntity.setSite(siteEntity);
        pageRepository.save(pageEntity);
    }

    @Transactional
    public void updateStatusSiteFailed(SiteEntity siteEntity, String lastError) {
        siteRepository.updateSiteStatusAndLastError(siteEntity.getId(), Status.INDEXING, LocalDateTime.now(), lastError);
        siteErrorMap.put(siteEntity.getId(), true);
    }

    public boolean checkConditionStatusSite(SiteEntity siteEntity) {
        return siteErrorMap.get(siteEntity.getId()) == true;
    }

    @Transactional
    public void updateStatusSiteIndexing(SiteEntity siteEntity) {
        siteRepository.updateSiteStatus(siteEntity.getId(), Status.INDEXING, LocalDateTime.now());
        siteErrorMap.put(siteEntity.getId(), false);
    }

    public void shutdownSiteForkJoinPool(int siteId) {
        ForkJoinPool pool = sitePools.get(siteId);
        if (pool != null) {
            log.info("Active threads before shutdown: {}", pool.getActiveThreadCount());
            log.info("Queued tasks: {}", pool.getQueuedTaskCount());
            pool.shutdown();
            try {
                if (!pool.awaitTermination(10, TimeUnit.MINUTES)) {
                    log.warn("ForkJoinPool for site {} did not terminate in time.", siteId);
                }
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for ForkJoinPool for site {}", siteId, e);
                Thread.currentThread().interrupt();
            }
            log.info("ForkJoinPool for site {} завершён.", siteId);
        }
    }

/*    @Override
    public void clearSiteState(int siteId) {
        siteErrorMap.remove(siteId);
        visitedUrls.removeIf(url -> url.startsWith(siteRepository.findById(siteId).get().getUrl()));
        //System.gc();
    }*/

/*    @Override
    public void clearSiteStateInBatch(int siteId) {
        long start = System.currentTimeMillis();
        log.info("Начинаем удаление пройденных сайтов, размер visit urls {}", visitedUrls.size());

        String siteUrl = siteRepository.findById(siteId).get().getUrl();
        int batchSize = 100;
        int offset = 0;
        List<String> urlsToRemove = visitedUrls.stream()
                .filter(url -> url.startsWith(siteUrl))
                .collect(Collectors.toList());

        while (offset < urlsToRemove.size()) {
            int end = Math.min(offset + batchSize, urlsToRemove.size());
            visitedUrls.removeAll(urlsToRemove.subList(offset, end));
            offset = end;
        }

        log.info("Очистка завершена, удалено {} URL-адресов, затраченное время: {} сек, для сайта {}",
                offset, (System.currentTimeMillis() - start) / 1000, siteUrl);
        log.info("Количество visitUrls после удаления {} сайта {}", siteUrl, visitedUrls.size());
    }*/

    public boolean isSiteProcessingCompleted(SiteEntity siteEntity) {
        return visitedUrls.stream().noneMatch(url -> url.startsWith(siteEntity.getUrl()));
    }

    @Override
    @Transactional
    public void deleteAllSiteAndPages(){
        lemmaProcessService.deleteAllLemmasAndIndexes();
        pageRepository.deleteAllPages();
        siteRepository.deleteAllSites();
    }

    public void shutdownAllPoolsImmediately() {
        sitePools.values().forEach(pool -> {
            pool.shutdownNow();
            log.info("Выполнена резкая остановка потоков без ожидания завершения текущих задач");
        });
        sitePools.clear();
    }
}
