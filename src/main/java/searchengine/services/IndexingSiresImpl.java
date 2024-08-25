package searchengine.services;

import org.springframework.http.ResponseEntity;
import searchengine.dto.response.ApiResponse;
import searchengine.services.interfaces.IndexingSitesService;

public class IndexingSiresImpl implements IndexingSitesService {

    private volatile boolean isIndexingStart = false;

    @Override
    public ResponseEntity<ApiResponse> startIndexing() {
        ApiResponse apiResponse = new ApiResponse();
        if(isIndexingStart){
            apiResponse.setResult(false);
            apiResponse.setMessageError("Индексация уже запущена");
        } else {
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
        //boolean isPageConsistsOfConfig = checkPageSingleIndexingArgument(page);
        /*if(checkPageSingleIndexingArgument){
            apiResponse.setResult(true);
        } else {
            apiResponse.setResult(false);
            apiResponse.setMessageError("Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле");
        }*/
        return ResponseEntity.ok(apiResponse);
    }
}
