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
import searchengine.utility.UtilCheckString;

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

    @Override
    protected void compute() {
        if (UtilCheckString.isFileUrl(url)) {
            return;
        }
        Set<SiteIndexingTask> subTasks = new HashSet<>();

        try {
            log.info("START PAGE: " + url);
            Connection connection = ConnectionUtil.getConnection(url, property.getReferrer(), property.getUserAgent());
            Document doc = connection.get();

            String uri = url.substring(siteEntity.getUrl().length());
            pageProcessService.processPage(uri, doc, siteEntity);

            if(pageProcessService.checkConditionStatusSite(siteEntity)){
                pageProcessService.updateStatusSiteIndexing(siteEntity);
            }

            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String subUrl = link.absUrl("href");
                if (subUrl.startsWith(UtilCheckString.reworkUrl(siteEntity.getUrl()))
                        && !pageProcessService.isUrlVisited(subUrl)) {
                    SiteIndexingTask subTask = new SiteIndexingTask(subUrl, siteEntity, property, pageProcessService);
                    subTasks.add(subTask);
                    subTask.fork();
                }
            }
        } catch (Exception e) {
            log.error("ERROR processing URL: " + url, e);
            int code = ConnectionUtil.getStatusCode(url);
            pageProcessService.updateStatusPage(siteEntity, url, code);
            log.info("Идем изменять статус " + siteEntity);
            pageProcessService.updateStatusSiteFailed(siteEntity, e.getMessage());
        }
        subTasks.forEach(SiteIndexingTask::join);
    }
}
