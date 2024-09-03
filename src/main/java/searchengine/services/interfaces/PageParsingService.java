package searchengine.services.interfaces;

import searchengine.entity.SiteEntity;

public interface PageParsingService {
    void parsePage(String url, SiteEntity siteEntity);
}
