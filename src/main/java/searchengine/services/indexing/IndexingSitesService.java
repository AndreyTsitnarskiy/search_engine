package searchengine.services.indexing;

import org.springframework.http.ResponseEntity;
import searchengine.dto.response.ApiResponse;

public interface IndexingSitesService {
    ResponseEntity<ApiResponse> startIndexing();
    ResponseEntity<ApiResponse> indexPage(String page);
    ResponseEntity<ApiResponse> stopIndexing();
}
