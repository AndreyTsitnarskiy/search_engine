package searchengine.services.indexing.interfaces;

import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.SiteEntity;

import java.util.List;

public interface SiteProcessService {

    @Transactional
    void parseSites(List<SiteEntity> siteUrls);
    void parseSite(SiteEntity siteEntity);
}
