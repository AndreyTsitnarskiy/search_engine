package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import searchengine.entity.*;
import searchengine.utility.ConnectionUtils;
import searchengine.utility.LemmasExecute;
import searchengine.utility.ProjectParameters;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public class PageTask extends RecursiveAction {

    private static final Logger errorLogger = LoggerFactory.getLogger("ERROR_LOGGER");
    private static final Logger businessLogger = LoggerFactory.getLogger("BUSINESS_LOGGER");

    private static final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
    @Getter
    private static final ConcurrentHashMap<SiteEntity, ConcurrentHashMap<String, LemmaEntity>> allLemmasBySiteId = new ConcurrentHashMap<>();
    @Getter
    private static final Map<SiteEntity, List<IndexEntity>> allIndexEntityByPage = new HashMap<>();
    private static final ConcurrentHashMap<SiteEntity, Statuses> statusSites = new ConcurrentHashMap<>();

    private final String pageUrl;
    private final SiteEntity siteEntity;
    private final PageParsingServiceImpl pageParsingService;
    private final ProjectParameters projectParameters;

    private static final Pattern FILE_EXTENSION_PATTERN = Pattern.compile(".*\\.(pdf|docx?|xlsx?|jpg|jpeg|gif|png|mp3|mp4|aac|json|csv|exe|apk|rar|zip|xml|jar|bin|svg|nc|webp|m|fig|eps)$", Pattern.CASE_INSENSITIVE);

    @Override
    protected void compute() {
        long startTime = System.currentTimeMillis();
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
            //businessLogger.info("START: " + pageUrl);
            Document doc = ConnectionUtils.getDocument(pageUrl, projectParameters.getReferrer(), projectParameters.getUserAgent());

            if (doc != null) {
                pageEntity.setContent(doc.html());
                pageEntity.setCode(200);
                updateStatus(siteEntity, Statuses.INDEXING);

                String bodyPage = doc.body().text();

                Elements links = doc.select("a[href]");
                for (Element link : links) {
                    String url = link.absUrl("href");
                    if (url.startsWith(siteEntity.getUrl()) && !visitedUrls.contains(url)) {
                        PageTask subTask = new PageTask(url, siteEntity, pageParsingService, projectParameters);
                        subTasks.add(subTask);
                        subTask.fork();
                    }
                }
                pageParsingService.savePageEntity(pageEntity);
                lemmaParseBody(bodyPage, siteEntity, pageEntity);
            } else {
                pageEntity.setCode(ConnectionUtils.getStatusCode(pageUrl));
                pageParsingService.updateSiteStatusFailed(siteEntity, Statuses.FAILED, "Document is null for URL: " + pageUrl);
            }
        } catch (Exception e) {
            errorLogger.error("Ошибка обработки URL: " + pageUrl + " для сайта: " + siteEntity.getUrl(), e);
            pageEntity.setCode(ConnectionUtils.getStatusCode(pageUrl));
            updateStatus(siteEntity, Statuses.FAILED);
            pageParsingService.updateSiteStatusFailed(siteEntity, Statuses.FAILED, "Ошибка: " + e.getMessage());
        }
        for (PageTask sub : subTasks) {
            sub.join();
        }
        long endTime = System.currentTimeMillis();
        businessLogger.info("compute для {} завершён. Время выполнения: {} мс", pageUrl, (endTime - startTime));
    }

    //==================================UPDATE STATUS==================================
    private void updateStatus(SiteEntity siteEntity, Statuses newStatus) {
        statusSites.compute(siteEntity, (key, currentStatus) -> {
            if (currentStatus == null || currentStatus != newStatus) {
                businessLogger.info("Обновление статуса для сайта {} на {}", siteEntity.getUrl(), newStatus);
                pageParsingService.updateSiteStatusIndexing(siteEntity, newStatus);
                return newStatus;
            }
            return currentStatus;
        });
    }

    //===================================SAVE AND UPDATE ENTITY==================================
    private void lemmaParseBody(String textBody, SiteEntity siteEntity, PageEntity pageEntity) {
        businessLogger.info("Start method lemmaParseBody " + pageEntity.getPath());
        long startTime = System.currentTimeMillis();

        ConcurrentHashMap<String, LemmaEntity> siteLemmas = allLemmasBySiteId
                .computeIfAbsent(siteEntity, k -> new ConcurrentHashMap<>());
        HashMap<String, Integer> lemmaWorldsMap = LemmasExecute.getLemmaMap(textBody);

        for (Map.Entry<String, Integer> entry : lemmaWorldsMap.entrySet()) {
            String lemma = entry.getKey();
            float frequencyByRank = entry.getValue();

            synchronized (siteLemmas) {
                LemmaEntity lemmaEntity = siteLemmas.get(lemma);

                if (lemmaEntity != null) {
                    lemmaEntity.setFrequency(lemmaEntity.getFrequency() + 1);
                } else {
                    lemmaEntity = new LemmaEntity();
                    lemmaEntity.setLemma(lemma);
                    lemmaEntity.setSiteEntity(siteEntity);
                    lemmaEntity.setFrequency(1);
                    siteLemmas.put(lemma, lemmaEntity);
                    pageParsingService.saveOneFrequencyLemmaEntity(lemmaEntity);
                }
                IndexEntity indexEntity = createIndexesEntryParseBody(pageEntity, lemmaEntity, frequencyByRank);
                saveToMapIndexEntityBySite(siteEntity, indexEntity);
            }
        }
        long endTime = System.currentTimeMillis();
        businessLogger.info("lemmaParseBody завершён. Время выполнения: {} мс",  (endTime - startTime));
    }

    private void saveToMapIndexEntityBySite(SiteEntity siteEntity, IndexEntity indexEntity){
        allIndexEntityByPage.computeIfAbsent(siteEntity, k -> new ArrayList<>()).add(indexEntity);
    }

    private IndexEntity createIndexesEntryParseBody(PageEntity pageEntity, LemmaEntity lemmaEntity, float rank) {
        IndexEntity indexEntity = new IndexEntity();
        indexEntity.setPageEntity(pageEntity);
        indexEntity.setLemmaEntity(lemmaEntity);
        indexEntity.setRank(rank);
        return indexEntity;
    }
}


