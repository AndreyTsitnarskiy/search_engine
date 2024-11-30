package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.response.ApiResponse;
import searchengine.entity.SiteEntity;
import searchengine.entity.Status;
import searchengine.repository.SiteRepository;
import searchengine.services.indexing.interfaces.IndexingSitesService;
import searchengine.services.indexing.interfaces.SIteProcessService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingSitesImpl implements IndexingSitesService {
    private volatile boolean isIndexingStart = false;
    private final SiteRepository siteRepository;
    private final SitesList sitesList;
    private final SIteProcessService sIteProcessService;
    private final ForkJoinPool forkJoinPool = new ForkJoinPool();

    @Override
    public ResponseEntity<ApiResponse> startIndexing() {
        saveAllSiteEntity();
        if(isIndexingStart){
            return ResponseEntity.ok(new ApiResponse(false, "Индексация не запущена"));
        } else {
            isIndexingStart = true;
            new Thread(this::indexingAllSites).start();
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
        forkJoinPool.shutdownNow();
        return ResponseEntity.ok(new ApiResponse(true, "Indexing stopped"));
    }

    private void indexingAllSites(){
        List<SiteEntity> siteEntityList = siteRepository.findAll();
        try {
            sIteProcessService.parseSites(siteEntityList);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isIndexingStart = false;
        }
    }

    private void saveAllSiteEntity(){
        for (Site sites : sitesList.getSites()){
            SiteEntity siteEntity = new SiteEntity();
            siteEntity.setStatusTime(LocalDateTime.now());
            siteEntity.setName(sites.getName());
            siteEntity.setStatus(Status.INDEXING);
            siteEntity.setUrl(sites.getUrl());
            siteRepository.save(siteEntity);
        }
    }
}
