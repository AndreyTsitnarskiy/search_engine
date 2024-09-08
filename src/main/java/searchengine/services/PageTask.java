package searchengine.services;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.entity.IndexEntity;
import searchengine.entity.LemmaEntity;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.utility.ConnectionUtils;
import searchengine.utility.LemmasExecute;
import searchengine.utility.ProjectParameters;

import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Pattern;


@Slf4j
public class PageTask extends RecursiveAction {

    private static final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
    @Getter
    private static final ConcurrentHashMap<SiteEntity, ConcurrentHashMap<String, LemmaEntity>> allLemmasBySiteId = new ConcurrentHashMap<>();
    @Getter
    private static final ConcurrentHashMap<PageEntity, Set<IndexEntity>> allIndexesByPage = new ConcurrentHashMap<>();

    private final String pageUrl;
    private final SiteEntity siteEntity;
    private final PageParsingServiceImpl pageParsingService;
    private final ProjectParameters projectParameters;

    private static final Pattern FILE_EXTENSION_PATTERN = Pattern.compile(".*\\.(pdf|docx?|xlsx?|jpg|jpeg|gif|png|mp3|mp4|aac|json|csv|exe|apk|rar|zip|xml|jar|bin|svg|nc|webp|m|fig|eps)$", Pattern.CASE_INSENSITIVE);

    public PageTask(String pageUtl, SiteEntity siteEntity, PageParsingServiceImpl pageParsingService, ProjectParameters projectParameters) {
        this.pageUrl = pageUtl;
        this.siteEntity = siteEntity;
        this.pageParsingService = pageParsingService;
        this.projectParameters = projectParameters;
    }

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
            //log.info("START: " + pageUrl);
            Document doc = ConnectionUtils.getDocument(pageUrl,
                    projectParameters.getReferrer(),
                    projectParameters.getUserAgent());

            pageEntity.setContent(doc.html());
            pageEntity.setCode(200);

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
        } catch (Exception e) {
            log.error("ERROR ");
            e.printStackTrace();
        }

        pageParsingService.savePageEntity(pageEntity);

        for (PageTask sub : subTasks) {
            sub.join();
        }
    }

    private void lemmaParseBody(String textBody, SiteEntity siteEntity, PageEntity pageEntity) {
        // Получаем карту лемм для текущего сайта
        ConcurrentHashMap<String, LemmaEntity> siteLemmas = allLemmasBySiteId
                .computeIfAbsent(siteEntity, k -> new ConcurrentHashMap<>());

        // Получаем карту лемм из текста
        HashMap<String, Integer> lemmaWorldsMap = LemmasExecute.getLemmaMap(textBody);

        for (Map.Entry<String, Integer> entry : lemmaWorldsMap.entrySet()) {
            String lemma = entry.getKey();
            float frequency = entry.getValue();
            LemmaEntity lemmaEntity = new LemmaEntity(siteEntity, lemma, 1);
            IndexEntity indexEntity = indexesParseBody(pageEntity, lemmaEntity, frequency);
            addToMapIndexesByPage(pageEntity, indexEntity);

            // Обновляем лемму в карте
            siteLemmas.merge(lemma, lemmaEntity,
                    (existing, newLemma) -> {
                        existing.setFrequency(existing.getFrequency() + 1);
                        return existing;
                    }
            );
        }
    }

    private IndexEntity indexesParseBody(PageEntity pageEntity, LemmaEntity lemmaEntity, float rank) {
        IndexEntity indexEntity = new IndexEntity();
        indexEntity.setPageEntity(pageEntity);
        indexEntity.setLemmaEntity(lemmaEntity);
        indexEntity.setRank(rank);
        return indexEntity;
    }

    private void addToMapIndexesByPage(PageEntity pageEntity, IndexEntity indexEntity) {
        if (allIndexesByPage.containsKey(pageEntity)) {
            allIndexesByPage.get(pageEntity).add(indexEntity);
        } else {
            allIndexesByPage.put(pageEntity, new HashSet<>());
        }
    }
}


