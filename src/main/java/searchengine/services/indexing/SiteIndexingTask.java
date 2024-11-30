package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.entity.SiteEntity;
import searchengine.utility.ConnectionUtil;
import searchengine.utility.PropertiesProject;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public class SiteIndexingTask extends RecursiveAction {
    private final String url;
    private final SiteEntity siteEntity;
    private final PropertiesProject property;
    private final PageProcessServiceImpl pageProcessService;

    private static final Pattern FILE_EXTENSION_PATTERN = Pattern.compile(".*\\.(pdf|docx?|xlsx?|jpg|jpeg|gif|png|mp3|mp4|aac|json|csv|exe|apk|rar|zip|xml|jar|bin|svg|nc|webp|m|fig|eps)$", Pattern.CASE_INSENSITIVE);

    @Override
    protected void compute() {
        if (FILE_EXTENSION_PATTERN.matcher(url).matches()) {
            log.info("Skipping file URL: {}", url);
            return;
        }

        Set<SiteIndexingTask> subTasks = new HashSet<>();
        try {
            log.info("START: " + url);
            Connection connection = ConnectionUtil.getConnection(url, property.getReferrer(), property.getUserAgent());
            Document doc = connection.get();

            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String subUrl = link.absUrl("href");
                if (subUrl.startsWith(siteEntity.getUrl()) && !pageProcessService.isUrlVisited(subUrl)) {
                    //log.info("Processing URL: {}", url);
                    String uri = url.substring(siteEntity.getUrl().length());
                    pageProcessService.processPage(uri, doc, siteEntity);
                    SiteIndexingTask subTask = new SiteIndexingTask(subUrl, siteEntity, property, pageProcessService);
                    subTasks.add(subTask);
                    subTask.fork();
                }
            }
        } catch (Exception e) {
            log.error("ERROR processing URL: " + url, e);
        }
        subTasks.forEach(SiteIndexingTask::join);
    }
}
