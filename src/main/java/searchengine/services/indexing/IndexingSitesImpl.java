package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.IndexingStateManager;
import searchengine.dto.response.ApiResponse;
import searchengine.services.indexing.interfaces.IndexingSitesService;
import searchengine.services.indexing.interfaces.PageProcessService;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingSitesImpl implements IndexingSitesService {

    private final PageProcessService pageProcessService;
    private final IndexingStateManager indexingStateManager;

    @Override
    public ResponseEntity<ApiResponse> startIndexing() {
        if (!indexingStateManager.startIndexing()) {
            return ResponseEntity.ok(ApiResponse.failure("Индексация уже запущена"));
        }

        pageProcessService.indexingAllSites();
        return ResponseEntity.ok(ApiResponse.success("Indexing started"));
    }

    @Override
    public ResponseEntity<ApiResponse> indexPage(String path) {
        log.info("INDEX PAGE: " + path);
        ApiResponse apiResponse = new ApiResponse(true, "Single parse");
        return ResponseEntity.ok(apiResponse);
    }

    @Override
    public ResponseEntity<ApiResponse> stopIndexing() {
        indexingStateManager.stopIndexing();
        return ResponseEntity.ok(ApiResponse.success("Indexing stopped"));
    }
}
