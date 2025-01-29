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
        log.info("Поток: {} - Начало обработки страницы: {}", Thread.currentThread().getName(), url);
        if (!siteTaskService.isValidUrl(url, siteEntity)) {
            log.warn("Поток: {} - Пропуск невалидного URL: {}", Thread.currentThread().getName(), url);
            return;
        }
        Document doc = siteTaskService.loadPageDocument(url, siteEntity);
        if (doc == null) {
            log.warn("Поток: {} - Пропуск страницы из-за ошибки загрузки: {}", Thread.currentThread().getName(), url);
            return;
        }
        String uri = url.substring(siteEntity.getUrl().length());
        PageEntity pageEntity = repositoryManager.processPage(uri, doc, siteEntity);
        siteTaskService.processLemmas(doc, siteEntity, pageEntity);
        log.info("Поток: {} - Завершена обработка страницы: {}", Thread.currentThread().getName(), url);

        if (statusManager.hasSiteErrors(siteEntity)) {
            statusManager.updateStatusSiteIndexing(siteEntity);
        }

        Elements links = doc.select("a[href]");
        Set<SiteTaskRecursive> subTasks = createSubTasks(links);
        if (subTasks.isEmpty()) {
            log.warn("Поток: {} - Нет задач для создания подзадач с URL: {}", Thread.currentThread().getName(), url);
        } else {
            log.info("Создано подзадач: {}", subTasks.size());
        }
        invokeAll(subTasks);
    }

    private Set<SiteTaskRecursive> createSubTasks(Elements links) {
        return links.stream()
                .map(link -> link.absUrl("href"))
                .filter(subUrl -> {
                    boolean isNew = visitedUrlsManager.isUrlVisited(subUrl);
                    log.debug("URL: {} уже посещен: {}", subUrl, !isNew);
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
