package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.response.ApiResponse;
import searchengine.services.indexing.interfaces.IndexingSitesService;
import searchengine.services.indexing.interfaces.PageProcessService;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingSitesImpl implements IndexingSitesService {
    private volatile boolean isIndexingStart = false;
    private final PageProcessService pageProcessService;

    @Override
    public ResponseEntity<ApiResponse> startIndexing() {
        //pageProcessService.deleteAllSiteAndPages();
        if(isIndexingStart){
            return ResponseEntity.ok(new ApiResponse(false, "Индексация не запущена"));
        } else {
            isIndexingStart = true;
            pageProcessService.indexingAllSites();
        }
        return ResponseEntity.ok(new ApiResponse(true, "Indexing started"));
    }

    @Override
    public ResponseEntity<ApiResponse> indexPage(String path) {
        log.info("INDEX PAGE: " + path);
        ApiResponse apiResponse = new ApiResponse(true, "Single parse");
        return ResponseEntity.ok(apiResponse);
    }

    @Override
    public ResponseEntity<ApiResponse> stopIndexing() {

        return ResponseEntity.ok(new ApiResponse(true, "Indexing stopped"));
    }
}
