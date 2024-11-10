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
        Set<LemmaEntity> lemmaEntitySetToSave = new HashSet<>();
        Set<IndexEntity> indexEntitySetToSave = new HashSet<>();

        try {
            //log.info("START: " + pageUrl);
            Document doc = ConnectionUtils.getDocument(pageUrl,
                    projectParameters.getReferrer(),
                    projectParameters.getUserAgent());

            pageEntity.setContent(doc.html());
            pageEntity.setCode(200);

            String bodyPage = doc.body().text();
            HashMap<String, Integer> lemmasAndCount = pageParsingService.parseTextLemma(bodyPage);
            lemmaEntitySetToSave = pageParsingService.parsingBodyOnLemmas(lemmasAndCount, siteEntity);
            indexEntitySetToSave = pageParsingService.parsingAndCreateIndexes(lemmaEntitySetToSave, pageEntity, lemmasAndCount);

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
        pageParsingService.saveLemmasBySite(lemmaEntitySetToSave);
        pageParsingService.saveAllIndexesByPage(indexEntitySetToSave);

        for (PageTask sub : subTasks) {
            sub.join();
        }
    }
}


