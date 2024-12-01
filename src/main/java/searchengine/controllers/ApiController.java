package searchengine.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.response.ApiResponse;
import searchengine.dto.search.ApiSearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.indexing.interfaces.IndexingSitesService;
import searchengine.services.search.SearchService;
import searchengine.services.statisitc.StatisticsService;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api")
@Validated
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingSitesService indexingSitesService;
    private final SearchService searchService;

    @Autowired
    public ApiController(StatisticsService statisticsService, IndexingSitesService indexingSitesService, SearchService searchService) {
        this.statisticsService = statisticsService;
        this.indexingSitesService = indexingSitesService;
        this.searchService = searchService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<ApiResponse> startIndexing() {
        return indexingSitesService.startIndexing();
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<ApiResponse> stopIndexing() {
        return indexingSitesService.stopIndexing();
    }

    @PostMapping(value = "/indexPage", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<ApiResponse> indexPage(@RequestParam(value = "url") String url) {
        return ResponseEntity.ok(indexingSitesService.indexPage(URLDecoder.decode(url, StandardCharsets.UTF_8)).getBody());
    }

    @GetMapping("/search")
    public ResponseEntity<ApiSearchResponse> search(@RequestParam(value = "query", required = false) String query,
                                                    @RequestParam(value = "site", required = false) String url,
                                                    @RequestParam(value = "offset", required = false) Integer offset,
                                                    @RequestParam(value = "limit", required = false) Integer limit) {
        log.info("Query: " + query + ", url: " + url + ", offset: " + offset + ", limit: " + limit);
        if(url == null || url.isEmpty()) {
            url = "";
        }
        else {
            url = URLDecoder.decode(url, StandardCharsets.UTF_8);
        }
        return ResponseEntity.ok(searchService.search(query, url, offset, limit).getBody());
    }
}
