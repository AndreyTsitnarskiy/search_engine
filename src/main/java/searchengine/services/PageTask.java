package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.utility.ConnectionUtils;
import searchengine.utility.ProjectParameters;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveAction;

@Slf4j
public class PageTask extends RecursiveAction {

    private static final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();

    private final String pageUrl;
    private final SiteEntity siteEntity;
    private final PageParsingServiceImpl pageParsingService;
    private final ProjectParameters projectParameters;

    public PageTask(String pageUtl, SiteEntity siteEntity, PageParsingServiceImpl pageParsingService, ProjectParameters projectParameters) {
        this.pageUrl = pageUtl;
        this.siteEntity = siteEntity;
        this.pageParsingService = pageParsingService;
        this.projectParameters = projectParameters;
    }

    @Override
    protected void compute() {
        // Проверяем, был ли URL уже посещен
        if (!visitedUrls.add(pageUrl)) {
            log.info("SKIPPED: " + pageUrl + " (already visited)");
            return; // Если URL уже посещен, то выходим из метода
        }

        Set<PageTask> subTasks = new HashSet<>();
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(pageUrl);
        pageEntity.setSiteEntity(siteEntity);

        try {
            log.info("START: " + pageUrl);
            Document doc = ConnectionUtils.getDocument(pageUrl,
                    projectParameters.getReferrer(),
                    projectParameters.getUserAgent());

            pageEntity.setContent(doc.html());
            pageEntity.setCode(200);

            Elements links = doc.select("a[href]");
            for (Element link : links){
                String url = link.absUrl("href");

                if(url.startsWith(siteEntity.getUrl()) && !visitedUrls.contains(url) && !url.contains(projectParameters.getFileExtensions())) {
                    PageTask subTask = new PageTask(url, siteEntity, pageParsingService, projectParameters);
                    subTasks.add(subTask);
                    subTask.fork();
                }
            }

        } catch (Exception e) {
            pageEntity.setCode(ConnectionUtils.getStatusCode(pageUrl));
            log.error("ERROR ");
            e.printStackTrace();
        }

        pageParsingService.savePageEntity(pageEntity);

        for (PageTask sub : subTasks) {
            sub.join();
        }
    }
}
