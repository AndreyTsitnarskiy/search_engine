package searchengine.services.indexing.interfaces;

public interface PageProcessService {
    void indexingAllSites();

    void reindexSinglePage(String path);

    void stopIndexing();

    void stopIndexingNow();
}
