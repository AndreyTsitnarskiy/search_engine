package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.response.ApiResponse;
import searchengine.services.interfaces.IndexingSitesService;
import searchengine.utility.TransformString;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IndexingSitesImpl implements IndexingSitesService {

    private volatile boolean isIndexingStart = false;
    private final SiteParsingServiceImpl siteParsingService;
    private final SitesList sitesList;

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
            //Наполнить логикой остановки
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
        List<Site> sites = sitesList.getSites();
        try {
            siteParsingService.parseSites(sites);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isIndexingStart = false;
        }
    }
}
