package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.utility.ProjectParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;

@Slf4j
public class PageTask extends RecursiveAction {

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
        List<PageTask> subTasks = new ArrayList<>();
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(pageUrl);
        pageEntity.setSiteEntity(siteEntity);

        try {
            log.info("START: " + pageUrl);
            Document doc = Jsoup.connect(pageUrl)
                            .userAgent(projectParameters.getUserAgent())
                                    .referrer(projectParameters.getReferrer())
                                            .ignoreHttpErrors(true)
                                                    .get();
            pageEntity.setContent(doc.html());
            pageEntity.setCode(200);

            Elements links = doc.select("a[href]");
            for (Element link : links){
                String url = link.absUrl("href");

                if(url.startsWith(siteEntity.getUrl())) {
                    PageTask subTask = new PageTask(url, siteEntity, pageParsingService, projectParameters);
                    subTasks.add(subTask);
                    subTask.fork();
                }
            }

        } catch (Exception e) {
            pageEntity.setCode(500);
            log.error("ERROR ");
            e.printStackTrace();
        }

        pageParsingService.savePageEntity(pageEntity);

        for (PageTask sub : subTasks) {
            sub.join();
        }
    }
}
