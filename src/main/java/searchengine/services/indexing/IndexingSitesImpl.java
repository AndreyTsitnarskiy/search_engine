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
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingSitesImpl implements IndexingSitesService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final SitesList sitesList;
    private final ForkJoinPool forkJoinPool = new ForkJoinPool();

    @Override
    public ResponseEntity<ApiResponse> startIndexing() {
        List<SiteEntity> sites = saveAndGetAllSiteEntity();
        sites.forEach(site -> {
            Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
            forkJoinPool.execute(new SiteIndexingTask(site.getUrl(), site, visitedUrls, siteRepository, pageRepository));
        });
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
        siteRepository.updateAllFailed("Индексация остановлена пользователем");
        return ResponseEntity.ok(new ApiResponse(true, "Indexing stopped"));
    }

    private List<SiteEntity> saveAndGetAllSiteEntity(){
        List<SiteEntity> siteEntityList = new ArrayList<>();
        for (Site sites : sitesList.getSites()){
            SiteEntity siteEntity = new SiteEntity();
            siteEntity.setStatusTime(LocalDateTime.now());
            siteEntity.setName(sites.getName());
            siteEntity.setStatus(Status.INDEXING);
            siteEntity.setUrl(sites.getUrl());
            siteEntityList.add(siteEntity);
        }
        return siteEntityList;
    }

}
