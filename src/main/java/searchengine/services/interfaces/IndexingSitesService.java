package searchengine.services.interfaces;

import org.springframework.http.ResponseEntity;
import searchengine.dto.response.ApiResponse;

public interface IndexingSitesService {

    ResponseEntity<ApiResponse> startIndexing();
    ResponseEntity<ApiResponse> stopIndexing();
    ResponseEntity<ApiResponse> singlePageIndexing(String page);
}
