package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
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

    @Override
    protected void compute() {
        log.info("Берем страницу: " + url);
        if (!siteTaskService.isValidUrl(url, siteEntity)) {
            log.warn("Пропущен невалидный URL: {}", url);
            return;
        }
        Document doc = siteTaskService.loadPageDocument(url, siteEntity);
        if (doc == null) {
            log.warn("Пропуск страницы из-за ошибки загрузки: {}", url);
            return;
        }
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
                        visitedUrlsManager))
                .collect(Collectors.toSet());
    }
}
