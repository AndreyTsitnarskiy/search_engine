package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.utility.ConnectionUtil;
import searchengine.utility.PropertiesProject;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveAction;

@Slf4j
@RequiredArgsConstructor
public class SiteIndexingTask extends RecursiveAction {
    private final String url;
    private final SiteEntity siteEntity;
    private final PropertiesProject property;
    private final PageProcessServiceImpl pageProcessService;

    private static final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();

    @Override
    protected void compute() {
        if (!visitedUrls.add(url)) {
            return;
        }
        if (property.getFileExtensions().matches(url)){
            return;
        }

        Set<SiteIndexingTask> siteIndexingTasks = new HashSet<>();
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(url);
        pageEntity.setSite(siteEntity);

        try {
            log.info("START: " + url);
            Document doc = ConnectionUtil.getDocument(url,
                    property.getReferrer(),
                    property.getUserAgent());

            pageEntity.setContent(doc.html());
            pageEntity.setCode(200);

            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String url = link.absUrl("href");

                if (url.startsWith(siteEntity.getUrl()) && !visitedUrls.contains(url)) {
                    SiteIndexingTask subTask = new SiteIndexingTask(url, siteEntity, property, pageProcessService);
                    siteIndexingTasks.add(subTask);
                    subTask.fork();
                }
            }
        } catch (Exception e) {
            log.error("ERROR ");
            e.printStackTrace();
        }

        pageProcessService.savePageEntity(pageEntity);

        for (SiteIndexingTask sub : siteIndexingTasks) {
            sub.join();
        }
    }
}
