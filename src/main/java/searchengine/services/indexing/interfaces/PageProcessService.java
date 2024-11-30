package searchengine.services.indexing.interfaces;

import searchengine.entity.SiteEntity;

public interface PageProcessService {
    void parsePage(String pageUrl, SiteEntity siteEntity);
}
