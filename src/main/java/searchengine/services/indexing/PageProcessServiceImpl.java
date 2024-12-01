package searchengine.services.indexing;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.entity.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.indexing.interfaces.PageProcessService;
import searchengine.utility.PropertiesProject;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
@RequiredArgsConstructor
public class PageProcessServiceImpl implements PageProcessService {

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final PropertiesProject projectParameters;
    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Boolean> siteErrorMap = new ConcurrentHashMap<>();
    private final Map<Integer, ForkJoinPool> sitePools = new ConcurrentHashMap<>();

    @Override
    public void parsePage(String pageUrl, Document document, SiteEntity siteEntity) {
        ForkJoinPool forkJoinPool = sitePools.computeIfAbsent(siteEntity.getId(), id -> new ForkJoinPool());
        try {
            forkJoinPool.invoke(new SiteIndexingTask(pageUrl, siteEntity, projectParameters, this));
            if (isSiteProcessingCompleted(siteEntity)) {
                shutdownSiteForkJoinPool(siteEntity.getId());
                log.info("Завершены все потоки для сайта: " + siteEntity.getUrl());
            }
        } catch (Exception e) {
            log.error("ERROR " + e.getMessage());
        }
    }

    @Override
    public void clearSiteState(int siteId) {
        siteErrorMap.remove(siteId);
        visitedUrls.removeIf(url -> url.startsWith(siteRepository.findById(siteId).get().getUrl()));
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
        Optional<SiteEntity> optionalSiteEntity = siteRepository.findById(siteEntity.getId());
        if (optionalSiteEntity.isPresent()) {
            SiteEntity site = optionalSiteEntity.get();
            site.setStatus(Status.FAILED);
            site.setStatusTime(LocalDateTime.now());
            site.setLastError(lastError);
            siteRepository.save(site);
            siteErrorMap.put(siteEntity.getId(), true);
        } else {
            throw new EntityNotFoundException("Сайт не найден для изменения статуса на FAILED");
        }
    }

    public boolean checkConditionStatusSite(SiteEntity siteEntity) {
        return siteErrorMap.get(siteEntity.getId()) == true;
    }

    @Transactional
    public void updateStatusSiteIndexing(SiteEntity siteEntity) {
        Optional<SiteEntity> optionalSiteEntity = siteRepository.findById(siteEntity.getId());
        if (optionalSiteEntity.isPresent()) {
            SiteEntity site = optionalSiteEntity.get();
            site.setStatus(Status.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
            siteErrorMap.put(siteEntity.getId(), false);
        } else {
            throw new EntityNotFoundException("Сайт не найден для изменения статуса на INDEXING");
        }
    }

    public void shutdownSiteForkJoinPool(int siteId) {
        ForkJoinPool pool = sitePools.get(siteId);
        if (pool != null) {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(10, TimeUnit.MINUTES)) {
                    log.warn("ForkJoinPool for site {} did not terminate within the timeout.", siteId);
                }
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for ForkJoinPool for site {} to terminate", siteId, e);
                Thread.currentThread().interrupt();
            }
            log.info("ForkJoinPool for site {} завершён.", siteId);
        }
    }

    public boolean isSiteProcessingCompleted(SiteEntity siteEntity) {
        return visitedUrls.stream().noneMatch(url -> url.startsWith(siteEntity.getUrl()));
    }
}
