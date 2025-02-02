package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.services.indexing.managers.ForkJoinPoolManager;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.entity.Status;
import searchengine.services.indexing.interfaces.PageProcessService;
import searchengine.services.indexing.managers.RepositoryManager;
import searchengine.services.indexing.managers.StatusManager;
import searchengine.services.indexing.managers.VisitedUrlsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageProcessServiceImpl implements PageProcessService {

    private final StatusManager statusManager;
    private final VisitedUrlsManager visitedUrlsManager;
    private final RepositoryManager repositoryManager;
    private final ForkJoinPoolManager forkJoinPoolManager;
    private final SiteTaskService siteTaskService;

    @Override
    public void indexingAllSites() {
        log.info("Начало индексации всех сайтов");
        repositoryManager.truncateAllSiteAndPages();
        List<SiteEntity> siteEntityList = repositoryManager.getListSiteEntity();
        log.info("Количество сайтов для индексации: {}", siteEntityList.size());
        if(siteEntityList.size() != 0){
            forkJoinPoolManager.restartIfNeeded();
        }
        try {
            parseSites(siteEntityList);
            stopIndexingNow();
        } catch (Exception e) {
            log.error("Ошибка индексации: {}", e.getMessage(), e);
        }
    }

    @Override
    public void stopIndexingNow() {
        log.warn("Принудительная остановка индексации!");
        forkJoinPoolManager.shutdownNow();
        List<SiteEntity> siteEntityList = repositoryManager.getAllSitesFromRepository();
        for (SiteEntity siteEntity : siteEntityList) {
            if (siteEntity.getStatus() != Status.INDEXED && siteEntity.getStatus() != Status.FAILED) {
                statusManager.updateStatusSiteFailed(siteEntity, "Индексация остановлена пользователем");
            }
            visitedUrlsManager.clearUrls(siteEntity.getUrl());
            statusManager.clearSiteState(siteEntity.getId());
        }
    }

    @Override
    public void stopIndexing() {
        forkJoinPoolManager.shutdown();
    }

    @Override
    public void reindexSinglePage(String url) {
        log.info("Запускаем реиндекс для: {}", url);

        Optional<SiteEntity> siteEntityOpt = repositoryManager.findSiteByUrl(url);
        if (siteEntityOpt.isEmpty()) {
            log.warn("Сайт не найден URL: {}", url);
            throw new IllegalArgumentException("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }
        SiteEntity siteEntity = siteEntityOpt.get();
        repositoryManager.deletePageAndAssociatedData(url, siteEntity);

        Document document = siteTaskService.loadPageDocument(url, siteEntity);
        if (document == null) {
            throw new IllegalArgumentException("Ошибка при загрузке страницы по URL: " + url);
        }

        String uri = url.substring(siteEntity.getUrl().length());
        PageEntity pageEntity = repositoryManager.processPage(uri, document, siteEntity);
        siteTaskService.processLemmas(document, siteEntity, pageEntity);

        log.info("Реиндекс URL: {} успешен", url);
    }

    private void parseSites(List<SiteEntity> sites) {
        List<SiteTaskRecursive> siteTasks = new ArrayList<>();

        for (SiteEntity siteEntity : sites) {
            statusManager.initializeSite(siteEntity);
            log.info("Начинаем парсинг: " + siteEntity.getUrl());
            siteTasks.add(new SiteTaskRecursive(siteEntity.getUrl(),
                    siteEntity,
                    statusManager,
                    repositoryManager,
                    siteTaskService,
                    visitedUrlsManager,
                    forkJoinPoolManager));
            visitedUrlsManager.isUrlVisited(siteEntity.getUrl());
        }
        log.info("Запускаем задачи ForkJoinPool. Количество задач: {}", siteTasks.size());
        forkJoinPoolManager.executeTasks(siteTasks);

        for (SiteEntity siteEntity : sites) {
            if (siteEntity.getStatus() != Status.FAILED) {
                statusManager.updateStatusIndexed(siteEntity);
            }
            visitedUrlsManager.clearUrls(siteEntity.getUrl());
            statusManager.clearSiteState(siteEntity.getId());
        }
    }
}
