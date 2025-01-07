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
import searchengine.utility.UtilCheck;

import java.rmi.ConnectException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.RecursiveAction;

@Slf4j
@RequiredArgsConstructor
public class SiteTask extends RecursiveAction {
    private final String url;
    private final SiteEntity siteEntity;
    private final PropertiesProject property;
    private final PageProcessServiceImpl pageProcessService;

    @Override
    protected void compute() {
        if (UtilCheck.isFileUrl(url)) {
            return;
        }
        String siteName = siteEntity.getUrl();
        if (!UtilCheck.containsSiteName(url, siteName)) {
            return;
        }

        Set<SiteTask> subTasks = new HashSet<>();

        try {
            //log.info("START PAGE: " + url);
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
                if (subUrl.startsWith(UtilCheck.reworkUrl(siteEntity.getUrl()))
                        && !pageProcessService.isUrlVisited(subUrl)) {
                    SiteTask subTask = new SiteTask(subUrl, siteEntity, property, pageProcessService);
                    subTasks.add(subTask);
                    subTask.fork();
                }
            }
        } catch (ConnectException ex) {
            log.warn("Ошибка подключения");
        } catch (Exception e) {
            //log.error("ERROR processing URL: " + url, e);
            int code = ConnectionUtil.getStatusCode(url);
            pageProcessService.updateStatusPage(siteEntity, url, code != 0 ? code : 500);
            pageProcessService.updateStatusSiteFailed(siteEntity, e.getMessage());
            return;
        }
        subTasks.forEach(SiteTask::join);
    }
}
