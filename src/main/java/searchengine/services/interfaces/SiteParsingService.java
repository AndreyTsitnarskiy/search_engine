package searchengine.services.interfaces;

import jakarta.transaction.Transactional;
import searchengine.entity.SiteEntity;

import java.util.List;

public interface SiteParsingService {

    @Transactional
    void parseSites(List<SiteEntity> siteUrls);

    void parseSite(SiteEntity siteEntity);
}
