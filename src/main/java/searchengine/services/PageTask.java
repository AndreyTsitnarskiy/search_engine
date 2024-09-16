package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.entity.*;
import searchengine.utility.ConnectionUtils;
import searchengine.utility.LemmasExecute;
import searchengine.utility.ProjectParameters;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public class PageTask extends RecursiveAction {

    private static final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
    @Getter
    private static final ConcurrentHashMap<SiteEntity, ConcurrentHashMap<String, LemmaEntity>> allLemmasBySiteId = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<SiteEntity, Statuses> statusSites = new ConcurrentHashMap<>();

    private final String pageUrl;
    private final SiteEntity siteEntity;
    private final PageParsingServiceImpl pageParsingService;
    private final ProjectParameters projectParameters;

    private static final Pattern FILE_EXTENSION_PATTERN = Pattern.compile(".*\\.(pdf|docx?|xlsx?|jpg|jpeg|gif|png|mp3|mp4|aac|json|csv|exe|apk|rar|zip|xml|jar|bin|svg|nc|webp|m|fig|eps)$", Pattern.CASE_INSENSITIVE);

    @Override
    protected void compute() {

        if (!visitedUrls.add(pageUrl)) {
            //log.info("SKIPPED: " + pageUrl + " (already visited)");
            return;
        }
        if (FILE_EXTENSION_PATTERN.matcher(pageUrl).matches()) {
            //log.info("SKIPPED: " + pageUrl + " (unwanted file extension)");
            return;
        }

        Set<PageTask> subTasks = new HashSet<>();
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(pageUrl);
        pageEntity.setSiteEntity(siteEntity);

        try {
            log.info("START: " + pageUrl);
            Document doc = ConnectionUtils.getDocument(pageUrl, projectParameters.getReferrer(), projectParameters.getUserAgent());

            if (doc != null) {
                pageEntity.setContent(doc.html());
                pageEntity.setCode(200);
                updateStatus(siteEntity, Statuses.INDEXING);

                String bodyPage = doc.body().text();
                lemmaParseBody(bodyPage, siteEntity, pageEntity);

                Elements links = doc.select("a[href]");
                for (Element link : links) {
                    String url = link.absUrl("href");
                    if (url.startsWith(siteEntity.getUrl()) && !visitedUrls.contains(url)) {
                        PageTask subTask = new PageTask(url, siteEntity, pageParsingService, projectParameters);
                        subTasks.add(subTask);
                        subTask.fork();
                    }
                }
            } else {
                log.error("Document is null for URL: " + pageUrl);
                pageEntity.setCode(ConnectionUtils.getStatusCode(pageUrl));
                pageParsingService.updateSiteStatusFailed(siteEntity, Statuses.FAILED, "Document is null for URL: " + pageUrl);
            }
        } catch (Exception e) {
            log.error("Error processing URL: " + pageUrl, e);
            pageEntity.setCode(ConnectionUtils.getStatusCode(pageUrl));  // Устанавливаем код ошибки
            updateStatus(siteEntity, Statuses.FAILED);
            pageParsingService.updateSiteStatusFailed(siteEntity, Statuses.FAILED, e.getMessage());
        }

        pageParsingService.savePageEntity(pageEntity);

        for (PageTask sub : subTasks) {
            sub.join();
        }
    }

    //==================================UPDATE STATUS==================================
    private void updateStatus(SiteEntity siteEntity, Statuses newStatus) {
        statusSites.compute(siteEntity, (key, currentStatus) -> {
            if (newStatus == Statuses.FAILED || (currentStatus != Statuses.FAILED && newStatus == Statuses.INDEXING)) {
                log.info("Обновление статуса для сайта {} на {}", siteEntity.getUrl(), newStatus);
                pageParsingService.updateSiteStatusIndexing(siteEntity, newStatus);
                return newStatus;
            }
            return currentStatus;
        });
        log.info("РАЗМЕР STATUSES: " + statusSites.size());
    }

    //===================================SAVE ENTITY==================================
    private void lemmaParseBody(String textBody, SiteEntity siteEntity, PageEntity pageEntity) {
        // Получаем карту лемм для текущего сайта
        ConcurrentHashMap<String, LemmaEntity> siteLemmas = allLemmasBySiteId
                .computeIfAbsent(siteEntity, k -> new ConcurrentHashMap<>());

        Set<IndexEntity> indexEntitySet = new HashSet<>();
        // Получаем карту лемм из текста
        HashMap<String, Integer> lemmaWorldsMap = LemmasExecute.getLemmaMap(textBody);

        for (Map.Entry<String, Integer> entry : lemmaWorldsMap.entrySet()) {
            String lemma = entry.getKey();
            float frequencyByRank = entry.getValue();
            LemmaEntity lemmaEntity = new LemmaEntity(siteEntity, lemma, 1);
            IndexEntity indexEntity = createIndexesEntryParseBody(pageEntity, lemmaEntity, frequencyByRank);
            indexEntitySet.add(indexEntity);

            // Обновляем лемму в карте
            siteLemmas.merge(lemma, lemmaEntity,
                    (existing, newLemma) -> {
                        existing.setFrequency(existing.getFrequency() + 1);
                        return existing;
                    }
            );
        }
        pageParsingService.saveAllIndexes(indexEntitySet);
    }

    private IndexEntity createIndexesEntryParseBody(PageEntity pageEntity, LemmaEntity lemmaEntity, float rank) {
        IndexEntity indexEntity = new IndexEntity();
        indexEntity.setPageEntity(pageEntity);
        indexEntity.setLemmaEntity(lemmaEntity);
        indexEntity.setRank(rank);
        return indexEntity;
    }
}


