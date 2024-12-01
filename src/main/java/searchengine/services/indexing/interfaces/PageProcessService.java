package searchengine.services.indexing.interfaces;

import org.jsoup.nodes.Document;
import searchengine.entity.SiteEntity;

public interface PageProcessService {
    void parsePage(String pageUrl, Document document, SiteEntity siteEntity);

    void initializeSite(int id);

    void clearSiteState(int id);
}
