package searchengine.services.indexing.interfaces;

import org.jsoup.nodes.Document;
import searchengine.entity.SiteEntity;

public interface PageProcessService {
    void indexingAllSites();
    void clearVisitUrls(int siteId);
    void deleteAllSiteAndPages();
    void processPage(String url, Document document, SiteEntity siteEntity);
}
