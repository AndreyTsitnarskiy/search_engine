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
import java.util.List;
import java.util.Set;
import java.util.concurrent.RecursiveAction;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class SiteTask extends RecursiveAction {

    private final String url;
    private final SiteEntity siteEntity;
    private final PropertiesProject property;
    private final PageProcessServiceImpl pageProcessService;
    private final StatusManagerImpl statusManager;

    @Override
    protected void compute() {
        if (UtilCheck.isFileUrl(url)) {
            return;
        }
        String siteName = siteEntity.getUrl();
        if (!UtilCheck.containsSiteName(url, siteName)) {
            return;
        }

        try {
            log.info("START PAGE: " + url);
            Connection connection = ConnectionUtil.getConnection(url, property.getReferrer(), property.getUserAgent());
            Document doc = connection.get();

            String uri = url.substring(siteEntity.getUrl().length());
            pageProcessService.processPage(uri, doc, siteEntity);

            if(statusManager.checkConditionStatusSite(siteEntity)){
                statusManager.updateStatusSiteIndexing(siteEntity);
            }

            Elements links = doc.select("a[href]");
            Set<SiteTask> subTasks = links.stream()
                    .map(link -> link.absUrl("href"))
                    .filter(subUrl -> {
                        boolean isNew = pageProcessService.isUrlVisited(subUrl);
                        return !isNew && subUrl.startsWith(UtilCheck.reworkUrl(siteEntity.getUrl())) &&
                                !subUrl.equals(siteEntity.getUrl());
                    })
                    .map(subUrl -> new SiteTask(subUrl, siteEntity, property, pageProcessService, statusManager))
                    .collect(Collectors.toSet());
            invokeAll(subTasks);

        } catch (ConnectException ex) {
            log.warn("Ошибка подключения");
        } catch (Exception e) {
            handleTaskError(url, e);
        }
    }

    private void handleTaskError(String url, Exception e) {
        log.error("Failed processing URL: {}", url, e);
        int code = ConnectionUtil.getStatusCode(url);
        statusManager.updateStatusPage(siteEntity, url, code != 0 ? code : 500);
        statusManager.updateStatusSiteFailed(siteEntity, e.getMessage());
    }
}
