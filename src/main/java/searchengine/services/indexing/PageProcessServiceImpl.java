package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.exceptions.SiteExceptions;
import searchengine.services.managers.ForkJoinPoolManager;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.services.indexing.interfaces.PageProcessService;
import searchengine.services.managers.RepositoryManager;
import searchengine.services.managers.StatusManager;
import searchengine.services.managers.VisitedUrlsManager;

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
        forkJoinPoolManager.restartIfNeeded();
        parseSites(siteEntityList);
        stopIndexing();
        visitedUrlsManager.clearAllSitesUrls(siteEntityList);
    }

    @Override
    public void stopIndexingNow() {
        log.warn("Принудительная остановка индексации!");
        forkJoinPoolManager.shutdownNow();
        String message = "Индексация остановлена пользователем";
        List<SiteEntity> siteEntityList = repositoryManager.getAllSitesFromRepository();
        statusManager.updateStatusesWhenUserStop(siteEntityList, message);
        visitedUrlsManager.clearAllSitesUrls(siteEntityList);
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
            throw new IllegalArgumentException("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }

        SiteEntity siteEntity = siteEntityOpt.get();
        repositoryManager.deletePageAndAssociatedData(url, siteEntity);

        Document document = siteTaskService.loadPageDocument(url);
        if (document == null) {
            log.error("Не удалось загрузить документ для URL: {}", url);
            throw new SiteExceptions("Не удалось загрузить документ для URL: " + url);
        }

        String uri = url.substring(siteEntity.getUrl().length());
        PageEntity pageEntity = repositoryManager.processPage(uri, document, siteEntity);
        siteTaskService.processLemmas(document, siteEntity, pageEntity);
        log.info("Реиндекс URL: {} успешен", url);
    }

    private void parseSites(List<SiteEntity> sites) {
        if (sites.isEmpty()) {
            log.warn("Список сайтов для индексации пуст");
            return;
        }
        List<SiteTaskRecursive> siteTasks = createSiteTasks(sites);
        log.info("Запускаем задачи ForkJoinPool. Количество задач: {}", siteTasks.size());
        forkJoinPoolManager.executeTasks(siteTasks);
        statusManager.updateAllSitesIndexed(sites);
        visitedUrlsManager.clearAllSitesUrls(sites);
    }

    private List<SiteTaskRecursive> createSiteTasks(List<SiteEntity> sites) {
        List<SiteTaskRecursive> tasks = new ArrayList<>();
        for (SiteEntity siteEntity : sites) {
            statusManager.initializeSite(siteEntity);
            log.info("Начинаем парсинг: {}", siteEntity.getUrl());
            tasks.add(new SiteTaskRecursive(
                    siteEntity.getUrl(),
                    siteEntity,
                    statusManager,
                    repositoryManager,
                    siteTaskService,
                    visitedUrlsManager,
                    forkJoinPoolManager
            ));
            visitedUrlsManager.isUrlVisited(siteEntity.getUrl());
        }
        return tasks;
    }
}
