package searchengine.services.indexing.interfaces;

import searchengine.entity.SiteEntity;

public interface StatusManager {
    void initializeSite(int siteId);
    void updateStatusPage(SiteEntity siteEntity, String url, int code);
    void updateStatusSiteFailed(SiteEntity siteEntity, String lastError);
    void updateStatusSiteIndexing(SiteEntity siteEntity);
    boolean checkConditionStatusSite(SiteEntity siteEntity);
    void clearSiteState(int siteId);
}
