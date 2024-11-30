package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.entity.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.utility.ConnectionUtil;
import searchengine.utility.PropertiesProject;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.RecursiveAction;

@Slf4j
@RequiredArgsConstructor
public class SiteIndexingTask extends RecursiveAction {
    private final String url;
    private final SiteEntity siteEntity;
    private final Set<String> visitedUrls;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final PropertiesProject property;

    @Override
    protected void compute() {
        if (!visitedUrls.add(url)) {
            return;
        }
        if (property.getFileExtensions().matches(url)){
            return;
        }

        Set<SiteIndexingTask> siteIndexingTasks = new HashSet<>();

        try {
            Connection.Response response = (Connection.Response) ConnectionUtil.getConnection(url,
                    property.getUserAgent(), property.getReferrer()).execute();

            int statusCode = response.statusCode();
            Document doc = response.parse();

            PageEntity page = new PageEntity(siteEntity, url, statusCode, doc.html());

            Elements links = doc.select("a[href]");
            for (Element link : links){
                String url = link.absUrl("href");
                if(url.startsWith(siteEntity.getUrl()) && !visitedUrls.contains(url)){
                    SiteIndexingTask subTask = new SiteIndexingTask(url, siteEntity, visitedUrls, siteRepository, pageRepository, property);
                    siteIndexingTasks.add(subTask);
                    subTask.fork();
                }
            }
            siteEntity.setStatus(Status.INDEXING);
            siteEntity.setStatusTime(LocalDateTime.now());
            log.info("PAGE: " + page);
            pageRepository.save(page);
            log.info("Save page: " + page.getPath());

        } catch (IOException e) {
            siteRepository.updateStatus(siteEntity.getId(), Status.FAILED, e.getMessage());
        }
        for (SiteIndexingTask sub : siteIndexingTasks){
            sub.join();
        }
    }
}
