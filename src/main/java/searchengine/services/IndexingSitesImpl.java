package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.response.ApiResponse;
import searchengine.entity.SiteEntity;
import searchengine.entity.Statuses;
import searchengine.repository.SiteRepository;
import searchengine.services.interfaces.IndexingSitesService;
import searchengine.utility.TransformString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingSitesImpl implements IndexingSitesService {

    private volatile boolean isIndexingStart = false;
    private final SiteParsingServiceImpl siteParsingService;
    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageParsingServiceImpl pageParsingService;

    @Override
    public ResponseEntity<ApiResponse> startIndexing() {
        ApiResponse apiResponse = new ApiResponse();
        if(isIndexingStart){
            apiResponse.setResult(false);
            apiResponse.setMessageError("Индексация уже запущена");
        } else {
            isIndexingStart = true;
            new Thread(this::indexingAllSites).start();
            //Наполнить логикой запуском индексации
            apiResponse.setResult(true);
        }
        return ResponseEntity.ok(apiResponse);
    }

    @Override
    public ResponseEntity<ApiResponse> stopIndexing() {
        ApiResponse apiResponse = new ApiResponse();
        if(isIndexingStart){
            log.info("Остановка процесса индексации...");
            isIndexingStart = false;
            pageParsingService.saveAllData();
            apiResponse.setResult(true);
        } else {
            apiResponse.setResult(false);
            apiResponse.setMessageError("Индексация не запущена");
        }
        return ResponseEntity.ok(apiResponse);
    }

    @Override
    public ResponseEntity<ApiResponse> singlePageIndexing(String page) {
        ApiResponse apiResponse = new ApiResponse();
        //Нужно провалидировать переданную страницу, создать метод, который будет проверять поадает страница в сайты из конфиг
        boolean isPageConsistsOfConfig = TransformString.checkPageSingleIndexingArgument(page);
        if(isPageConsistsOfConfig){
            apiResponse.setResult(true);
        } else {
            apiResponse.setResult(false);
            apiResponse.setMessageError("Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле");
        }
        return ResponseEntity.ok(apiResponse);
    }

    private void indexingAllSites(){
        List<SiteEntity> siteEntityList = getListSiteEntity();
        try {
            siteParsingService.parseSites(siteEntityList);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isIndexingStart = false;
        }
    }

    private List<SiteEntity> getListSiteEntity(){
        List<Site> sites = sitesList.getSites();
        List<SiteEntity> siteEntityList = new ArrayList<>();
        for (Site site : sites){
            SiteEntity siteEntity = new SiteEntity();
            siteEntity.setName(site.getName());
            siteEntity.setUrl(site.getUrl());
            siteEntity.setLocalDateTime(LocalDateTime.now());
            siteEntity.setStatuses(Statuses.INDEXING);
            siteEntityList.add(siteEntity);
            siteRepository.save(siteEntity);
        }
        return siteEntityList;
    }
}
