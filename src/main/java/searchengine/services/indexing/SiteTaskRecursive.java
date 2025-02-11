package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import searchengine.entity.Status;
import searchengine.exceptions.SiteExceptions;
import searchengine.services.managers.ForkJoinPoolManager;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.services.managers.RepositoryManager;
import searchengine.services.managers.StatusManager;
import searchengine.services.managers.VisitedUrlsManager;
import searchengine.utility.ConnectionUtil;
import searchengine.utility.UtilCheck;

import java.util.Collections;
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
        //log.debug("Начало обработки страницы: {}", url);
        if (siteEntity.getStatus() == Status.FAILED) {
            log.warn("Сайт {} уже имеет статус FAILED, пропускаем задачу для {}", siteEntity.getUrl(), url);
            return;
        }

        try {
            if (forkJoinPoolManager.isIndexingStopped()) {
                log.warn("Индексация остановлена, прерываем задачу для {}", url);
                return;
            }
            if (!siteTaskService.isValidUrl(url, siteEntity)) {
                log.debug("URL {} не соответствует корню сайта, пропускаем", url);
                return;
            }
            Document doc = siteTaskService.loadPageDocument(url);
            String uri = url.substring(siteEntity.getUrl().length());
            PageEntity pageEntity = repositoryManager.processPage(uri, doc, siteEntity);
            siteTaskService.processLemmas(doc, siteEntity, pageEntity);

/*Реализация с изменением статуса с failed на indexing если следующая задача успешно обработана
            if (statusManager.hasSiteErrors(siteEntity)) {
                statusManager.updateStatusSiteIndexing(siteEntity);
            }
*/

            Elements links = doc.select("a[href]");
            Set<SiteTaskRecursive> subTasks = createSubTasks(links);
            invokeAll(subTasks);
        } catch (Exception e) {
            log.error("Ошибка при обработке страницы: {}", url, e);
            handleTaskError(url, e, siteEntity);
            throw new SiteExceptions("Ошибка при обработке страницы: " + url, e);
        }
    }

    private Set<SiteTaskRecursive> createSubTasks(Elements links) {
        if (siteEntity.getStatus() == Status.FAILED) {
            log.warn("Сайт {} уже имеет статус FAILED, пропускаем создание подзадач", siteEntity.getUrl());
            return Collections.emptySet();
        }

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

    private void handleTaskError(String url, Exception e, SiteEntity siteEntity) {
        int code = ConnectionUtil.getStatusCode(url);
        statusManager.updateStatusPage(siteEntity, url, code != 0 ? code : 500);
        statusManager.updateStatusSiteFailed(siteEntity, e.getMessage());
    }
}
