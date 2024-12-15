package searchengine.services.indexing.interfaces;

import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;

import java.util.List;

public interface LemmaProcessService {
    void parsingAndSaveContent(SiteEntity siteEntity, List<PageEntity> listPages);
}
