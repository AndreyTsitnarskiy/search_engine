package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.response.IndexingRequest;
import searchengine.dto.response.IndexingResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.indexing.IndexingSitesService;
import searchengine.services.statisitc.StatisticsService;

@RestController
@RequestMapping("/api")
@Validated
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingSitesService indexingSitesService;

    public ApiController(StatisticsService statisticsService, IndexingSitesService indexingSitesService) {
        this.statisticsService = statisticsService;
        this.indexingSitesService = indexingSitesService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IndexingResponse startIndexing(){
        return indexingSitesService.startIndexing();
    }

    @GetMapping("/stopIndexing")
    @ResponseStatus(HttpStatus.OK)
    public IndexingResponse stopIndexing(){
        return indexingSitesService.stopIndexing();
    }

    @PostMapping(value = "/indexingPage", consumes =  {MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IndexingResponse singlePageIndexing(@Validated IndexingRequest indexingRequest) {
        return indexingSitesService.singlePageIndexing(indexingRequest);
    }
}
