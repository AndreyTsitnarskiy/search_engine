package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.response.ApiResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.indexing.IndexingSitesService;
import searchengine.services.statisitc.StatisticsService;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api")
@Validated
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingSitesService indexingSitesService;

    @Autowired
    public ApiController(StatisticsService statisticsService, IndexingSitesService indexingSitesService) {
        this.statisticsService = statisticsService;
        this.indexingSitesService = indexingSitesService;
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
}
