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
        ForkJoinPool forkJoinPool = sitePools.computeIfAbsent(siteEntity.getId(),
                id -> new ForkJoinPool(UtilCheck.calculateForkJoinPoolSize(sitesList.getSites().size())));
        try {
            forkJoinPool.invoke(new SiteTask(pageUrl, siteEntity, projectParameters, this));
            if (isSiteProcessingCompleted(siteEntity)) {
                shutdownSiteForkJoinPool(siteEntity.getId());
                log.info("Завершены все потоки для сайта: " + siteEntity.getUrl());
                int totalPageCount = pageRepository.countPagesBySiteId(siteEntity.getId());
                if(totalPageCount != 0) {
                    batchProcessingLemmaAndIndex(siteEntity, 100, totalPageCount);
                }
            }
        } catch (Exception e) {
            log.error("ERROR " + e.getMessage());
        }
    }

    @Override
    public void tempMethodTests(){
        List<SiteEntity> siteEntityList = siteRepository.findAll();
        for (SiteEntity site : siteEntityList){
            int totalPageCount = pageRepository.countPagesBySiteId(site.getId());
            batchProcessingLemmaAndIndex(site, 100, totalPageCount);
        }
    }

    private void batchProcessingLemmaAndIndex(SiteEntity siteEntity, int batchSize, int totalPageCount){
        Runtime runtime = Runtime.getRuntime();
        log.info("Количество страниц {} для обработки сайта {}", totalPageCount, siteEntity.getUrl());
        int offset = 0;
        while (offset < totalPageCount) {
            long start = System.currentTimeMillis();
            List<PageEntity> pageBatch = pageRepository.findPagesBySiteIdWithPagination(siteEntity.getId(), batchSize, offset);
            log.info("Обработка батча с {} до {}", offset, offset + pageBatch.size());
            lemmaProcessService.parsingAndSaveContent(siteEntity, pageBatch);
            long finish = System.currentTimeMillis();
            log.info("Время обработки бача {} секунд", (float) ((finish - start) / 1000));
            log.info("Свободная память: {} MB", runtime.freeMemory() / 1024 / 1024);
            offset += batchSize;
        }
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

    @Override
    public void clearSiteState(int siteId) {
        siteErrorMap.remove(siteId);
        visitedUrls.removeIf(url -> url.startsWith(siteRepository.findById(siteId).get().getUrl()));
        System.gc();
    }

    public boolean isSiteProcessingCompleted(SiteEntity siteEntity) {
        return visitedUrls.stream().noneMatch(url -> url.startsWith(siteEntity.getUrl()));
    }
}
