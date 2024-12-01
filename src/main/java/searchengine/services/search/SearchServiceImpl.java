package searchengine.services.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.search.ApiSearchResponse;

@Slf4j
@Service
public class SearchServiceImpl implements SearchService {

    @Override
    public ResponseEntity<ApiSearchResponse> search(String query, String url, int offset, int limit) {
        log.info("Received search request with query: {}, url: {}, offset: {}, limit: {}", query, url, offset, limit);
        //getSiteEntityList(url);
        ApiSearchResponse apiSearchResponse = new ApiSearchResponse();
        apiSearchResponse.setResult(false);
 /*       if(checkQuery(query) || checkLemmaInDatabase(query)){
            apiSearchResponse.setMessageError("Query is incorrect or empty");
        } else if (checkStatusSites(url)){
            apiSearchResponse.setMessageError("Page is located outside the sites specified in the configuration file");
        } else {
            buildStringListAndLemmaEntityList(query);
            apiSearchResponse = getApiSearchResponse();
        }*/
        return ResponseEntity.ok(apiSearchResponse);
    }
}
