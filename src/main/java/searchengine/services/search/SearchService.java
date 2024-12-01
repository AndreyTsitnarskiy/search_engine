package searchengine.services.search;

import org.springframework.http.ResponseEntity;
import searchengine.dto.search.ApiSearchResponse;

public interface SearchService {
    ResponseEntity<ApiSearchResponse> search(String query, String url, int offset, int limit);
}
