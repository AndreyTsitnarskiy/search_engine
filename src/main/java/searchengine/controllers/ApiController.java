package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingSitesService indexingSitesService;
    private final SearchService searchService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<ApiResponse> startIndexing() {
        log.info("Запрос на старт индексации получен");
        ApiResponse response = indexingSitesService.startIndexing().getBody();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<ApiResponse> stopIndexing() {
        log.info("Запрос на остановку индексации получен");
        return indexingSitesService.stopIndexing();
    }

    @PostMapping(value = "/indexPage", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<ApiResponse> indexPage(@RequestParam(value = "url") String url) {
        return indexingSitesService.indexPage(URLDecoder.decode(url, StandardCharsets.UTF_8));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiSearchResponse> search(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "site", required = false) String url,
            @RequestParam(value = "offset", required = false) Integer offset,
            @RequestParam(value = "limit", required = false) Integer limit) {

        log.info("query: {}, url: {}, offset: {}, limit: {}", query, url, offset, limit);
        
        String decodedUrl = (url == null || url.isEmpty()) ? "" : URLDecoder.decode(url, StandardCharsets.UTF_8);
        return ResponseEntity.ok(searchService.search(query, decodedUrl, offset, limit).getBody());
    }
}
