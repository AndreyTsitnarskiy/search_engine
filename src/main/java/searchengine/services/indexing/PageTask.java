package searchengine.services.indexing;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import searchengine.entity.SiteEntity;
import searchengine.utility.ConnectionUtil;

import java.util.List;
import java.util.concurrent.RecursiveAction;

@Slf4j
public class PageTask extends RecursiveAction {

    private final String url;
    private final SiteEntity siteEntity;
    private final PageProcessServiceImpl pageProcessService;

    public PageTask(String url, SiteEntity siteEntity, PageProcessServiceImpl pageProcessService) {
        this.url = url;
        this.siteEntity = siteEntity;
        this.pageProcessService = pageProcessService;
    }

    @Override
    protected void compute() {
        if (pageProcessService.isUrlVisited(url)) {
            return;
        }

        try {
            Document doc = ConnectionUtil.getConnection(url, "referrer", "user-agent").get();
            pageProcessService.savePage(url, doc.html(), 200, siteEntity);

            List<PageTask> subTasks = doc.select("a[href]").stream()
                    .map(link -> new PageTask(link.absUrl("href"), siteEntity, pageProcessService))
                    .toList();

            invokeAll(subTasks);
        } catch (Exception e) {
            log.error("Ошибка обработки страницы {}: {}", url, e.getMessage(), e);
            pageProcessService.savePage(url, "", 500, siteEntity);
        }
    }
}
