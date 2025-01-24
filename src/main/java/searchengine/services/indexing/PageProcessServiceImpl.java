package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.entity.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.indexing.interfaces.LemmaProcessService;
import searchengine.services.indexing.interfaces.PageProcessService;
import searchengine.services.indexing.interfaces.StatusManager;
import searchengine.utility.PropertiesProject;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageProcessServiceImpl implements PageProcessService {

    private final SitesList sitesList;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final PropertiesProject projectParameters;
    private final LemmaProcessService lemmaProcessService;
    private final StatusManagerImpl statusManager;
    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
    private final ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();

    @Override
    public void indexingAllSites(){
        List<SiteEntity> siteEntityList = getListSiteEntity();
        try {
            parseSites(siteEntityList);
            shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Transactional
    private void parseSites(List<SiteEntity> sites) {
        List<SiteTask> siteTasks = new ArrayList<>();

        for (SiteEntity siteEntity : sites) {
            siteEntity.setStatus(Status.INDEXING);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);

            statusManager.initializeSite(siteEntity.getId());
            log.info("START PARSING: " + siteEntity.getUrl());
            siteTasks.add(new SiteTask(siteEntity.getUrl(), siteEntity, projectParameters, this, statusManager));
            visitedUrls.add(siteEntity.getUrl());
        }

        siteTasks.forEach(ForkJoinTask::fork);
        siteTasks.forEach(ForkJoinTask::join);

        for (SiteEntity siteEntity : sites) {
            try {
                if (siteEntity.getStatus() != Status.FAILED) {
                    siteEntity.setStatus(Status.INDEXED);
                    siteRepository.save(siteEntity);
                    clearVisitUrls(siteEntity.getId());
                    batchProcessingLemmaAndIndex(siteEntity);
                }
                statusManager.clearSiteState(siteEntity.getId());
            } catch (Exception e) {
                log.error("Error finalizing site {}", siteEntity.getUrl(), e);
                siteEntity.setStatus(Status.FAILED);
                siteEntity.setStatusTime(LocalDateTime.now());
                siteEntity.setLastError(e.getMessage());
                siteRepository.save(siteEntity);
            }
        }
    }

    private List<SiteEntity> getListSiteEntity(){
        List<Site> sites = sitesList.getSites();
        List<SiteEntity> siteEntityList = new ArrayList<>();
        for (Site site : sites){
            SiteEntity siteEntity = new SiteEntity();
            siteEntity.setName(site.getName());
            siteEntity.setUrl(site.getUrl());
            siteEntity.setStatusTime(LocalDateTime.now());
            siteEntity.setStatus(Status.INDEXING);
            siteEntityList.add(siteEntity);
            siteRepository.save(siteEntity);
        }
        return siteEntityList;
    }

    @Transactional
    private void batchProcessingLemmaAndIndex(SiteEntity siteEntity){
        log.info("ЗАПУЩЕН парсинг лем страниц сайта {}", siteEntity.getUrl());
        int totalPageCount = pageRepository.countPagesBySiteId(siteEntity.getId());
        int batchSize = 100;
        int offset = 0;
        if (totalPageCount != 0) {
            while (offset < totalPageCount) {
                List<PageEntity> pageBatch = pageRepository.findPagesBySiteIdWithPagination(siteEntity.getId(), batchSize, offset);
                lemmaProcessService.parsingAndSaveContent(siteEntity, pageBatch, forkJoinPool);
                offset += batchSize;
            }
        }
        log.info("ЗАВЕРШЕН парсинг лем страниц сайта {}", siteEntity.getUrl());
    }

    public boolean isUrlVisited(String url) {
        return !visitedUrls.add(url);
    }

    @Override
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

    @Override
    public void clearVisitUrls(int siteId) {
        visitedUrls.removeIf(url -> url.startsWith(siteRepository.findById(siteId).get().getUrl()));
    }

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

    public void shutdown() {
        forkJoinPool.shutdown();
        try {
            if (!forkJoinPool.awaitTermination(60, TimeUnit.SECONDS)) {
                forkJoinPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            forkJoinPool.shutdownNow();
        }
    }
}
