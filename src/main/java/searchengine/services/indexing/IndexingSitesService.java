package searchengine.services.indexing;

import searchengine.dto.response.IndexingRequest;
import searchengine.dto.response.IndexingResponse;

public interface IndexingSitesService {

    IndexingResponse startIndexing();
    IndexingResponse stopIndexing();
    IndexingResponse singlePageIndexing(IndexingRequest page);
}
