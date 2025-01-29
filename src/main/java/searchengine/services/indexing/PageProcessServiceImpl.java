package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.ForkJoinPoolManager;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.entity.Status;
import searchengine.services.indexing.interfaces.PageProcessService;

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
        log.info("Поток: {} - Начало индексации всех сайтов", Thread.currentThread().getName());
        repositoryManager.truncateAllSiteAndPages();
        List<SiteEntity> siteEntityList = repositoryManager.getListSiteEntity();
        try {
            parseSites(siteEntityList);
            forkJoinPoolManager.shutdown();
        } catch (Exception e) {
            log.error("Поток: {} - Ошибка при индексации всех сайтов: {}", Thread.currentThread().getName(), e.getMessage());
        }
    }

    @Override
    public void reindexSinglePage(String url) {
        log.info("Запускаем реиндекс для: {}", url);

        Optional<SiteEntity> siteEntityOpt = repositoryManager.findSiteByUrl(url);
        if (siteEntityOpt.isEmpty()) {
            log.warn("Сайт не найден URL: {}", url);
            return;
        }
        SiteEntity siteEntity = siteEntityOpt.get();
        repositoryManager.deletePageAndAssociatedData(url, siteEntity);

        Document document = siteTaskService.loadPageDocument(url, siteEntity);

        if (document == null) {
            return;
        }

        String uri = url.substring(siteEntity.getUrl().length());
        PageEntity pageEntity = repositoryManager.processPage(uri, document, siteEntity);
        siteTaskService.processLemmas(document, siteEntity, pageEntity);

        log.info("Реиндекс URL: {} успешен", url);
    }

    @Override
    public void stopIndexing() {
        forkJoinPoolManager.shutdown();
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
                    visitedUrlsManager));
            visitedUrlsManager.isUrlVisited(siteEntity.getUrl());
        }

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
