package searchengine.services.interfaces;

import jakarta.transaction.Transactional;
import searchengine.config.Site;

import java.util.List;

public interface SiteParsingService {

    @Transactional
    void parseSites(List<Site> siteUrls);

    void parseSite(Site site);
}
