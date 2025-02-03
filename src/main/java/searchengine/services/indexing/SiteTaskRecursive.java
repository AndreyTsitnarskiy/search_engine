package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import searchengine.services.indexing.managers.ForkJoinPoolManager;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.services.indexing.managers.RepositoryManager;
import searchengine.services.indexing.managers.StatusManager;
import searchengine.services.indexing.managers.VisitedUrlsManager;
import searchengine.utility.UtilCheck;

import java.util.Set;
import java.util.concurrent.RecursiveAction;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class SiteTaskRecursive extends RecursiveAction {

    private final String url;
    private final SiteEntity siteEntity;
    private final StatusManager statusManager;
    private final RepositoryManager repositoryManager;
    private final SiteTaskService siteTaskService;
    private final VisitedUrlsManager visitedUrlsManager;
    private final ForkJoinPoolManager forkJoinPoolManager;

    @Override
    protected void compute() {
        //log.info("Поток: {} - Начало обработки страницы: {}", Thread.currentThread().getName(), url);
        if (forkJoinPoolManager.isIndexingStopped()) {
            log.warn("Индексация остановлена, прерываем задачу для {}", url);
            return;
        }
        if (!siteTaskService.isValidUrl(url, siteEntity)) {
            return;
        }
        Document doc = siteTaskService.loadPageDocument(url, siteEntity);
        String uri = url.substring(siteEntity.getUrl().length());
        PageEntity pageEntity = repositoryManager.processPage(uri, doc, siteEntity);
        siteTaskService.processLemmas(doc, siteEntity, pageEntity);

        if (statusManager.hasSiteErrors(siteEntity)) {
            statusManager.updateStatusSiteIndexing(siteEntity);
        }

        Elements links = doc.select("a[href]");
        invokeAll(createSubTasks(links));
    }

    private Set<SiteTaskRecursive> createSubTasks(Elements links) {
        return links.stream()
                .map(link -> link.absUrl("href"))
                .filter(subUrl -> {
                    boolean isNew = visitedUrlsManager.isUrlVisited(subUrl);
                    return !isNew && subUrl.startsWith(UtilCheck.reworkUrl(siteEntity.getUrl())) &&
                            !subUrl.equals(siteEntity.getUrl());
                })
                .map(subUrl -> new SiteTaskRecursive(subUrl,
                        siteEntity,
                        statusManager,
                        repositoryManager,
                        siteTaskService,
                        visitedUrlsManager,
                        forkJoinPoolManager))
                .collect(Collectors.toSet());
    }
}
