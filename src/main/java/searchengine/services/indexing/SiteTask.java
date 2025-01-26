package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.LemmaEntity;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.utility.ConnectionUtil;
import searchengine.utility.LemmaExecute;
import searchengine.utility.PropertiesProject;
import searchengine.utility.UtilCheck;

import java.rmi.ConnectException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RecursiveAction;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class SiteTask extends RecursiveAction {

    private final String url;
    private final SiteEntity siteEntity;
    private final PropertiesProject property;
    private final PageProcessServiceImpl pageProcessService;
    private final StatusManager statusManager;
    private final VisitedUrlsManager visitedUrlsManager;
    private final RepositoryManager repositoryManager;

    @Override
    protected void compute() {
        log.info("START PAGE: " + url);
        if (!isValidUrl(url)) {
            log.info("Пропущен невалидный URL: {}", url);
            return;
        }
        Document doc = pageDocument(url);
        if (doc == null) {
            log.warn("Пропуск страницы из-за ошибки загрузки: {}", url);
            return;
        }
        String uri = url.substring(siteEntity.getUrl().length());
        PageEntity pageEntity = repositoryManager.processPage(uri, doc, siteEntity);
        processLemmas(doc, siteEntity, pageEntity);

        if (statusManager.hasSiteErrors(siteEntity)) {
            statusManager.updateStatusSiteIndexing(siteEntity);
        }

        Elements links = doc.select("a[href]");
        invokeAll(createSubTasks(links));
    }

    private Set<SiteTask> createSubTasks(Elements links) {
        return links.stream()
                .map(link -> link.absUrl("href"))
                .filter(subUrl -> {
                    boolean isNew = visitedUrlsManager.isUrlVisited(subUrl);
                    return !isNew && subUrl.startsWith(UtilCheck.reworkUrl(siteEntity.getUrl())) &&
                            !subUrl.equals(siteEntity.getUrl());
                })
                .map(subUrl -> new SiteTask(subUrl,
                        siteEntity,
                        property,
                        pageProcessService,
                        statusManager,
                        visitedUrlsManager,
                        repositoryManager))
                .collect(Collectors.toSet());
    }

    private Document pageDocument(String url) {
        try {
            Connection connection = ConnectionUtil.getConnection(url, property.getReferrer(), property.getUserAgent());
            return connection.get();
        } catch (ConnectException ex) {
            log.warn("Ошибка подключения");
            return null;
        } catch (Exception e) {
            handleTaskError(url, e);
            return null;
        }
    }

    private boolean isValidUrl(String url) {
        String siteName = siteEntity.getUrl();
        return !UtilCheck.isFileUrl(url) && UtilCheck.containsSiteName(url, siteName);
    }

    private void handleTaskError(String url, Exception e) {
        log.error("Failed processing URL: {}", url, e);
        int code = ConnectionUtil.getStatusCode(url);
        statusManager.updateStatusPage(siteEntity, url, code != 0 ? code : 500);
        statusManager.updateStatusSiteFailed(siteEntity, e.getMessage());
    }

    @Transactional
    private void processLemmas(Document document, SiteEntity siteEntity, PageEntity pageEntity) {
        try {
            String text = document.body().text();
            Map<String, Integer> lemmaMap = LemmaExecute.getLemmaMap(text);

            for (Map.Entry<String, Integer> lemmaEntry : lemmaMap.entrySet()) {
                String lemma = lemmaEntry.getKey();
                Integer frequency = lemmaEntry.getValue();

                LemmaEntity lemmaEntity = repositoryManager.processAndSaveLemma(siteEntity, lemma);
                repositoryManager.saveIndex(pageEntity, lemmaEntity, frequency);
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке лемм для страницы: {}", e.getMessage());
        }
    }
}
