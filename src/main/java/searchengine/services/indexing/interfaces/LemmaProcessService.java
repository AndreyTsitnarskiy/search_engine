package searchengine.services.indexing.interfaces;

import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;

import java.util.List;
import java.util.concurrent.ForkJoinPool;

public interface LemmaProcessService {
    void parsingAndSaveContent(SiteEntity siteEntity, List<PageEntity> listPages, ForkJoinPool forkJoinPool);
    void deleteAllLemmasAndIndexes();
}
