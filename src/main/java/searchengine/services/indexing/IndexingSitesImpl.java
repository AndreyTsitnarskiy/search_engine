package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.response.ApiResponse;
import searchengine.services.indexing.interfaces.IndexingSitesService;
import searchengine.services.indexing.interfaces.PageProcessService;
import searchengine.services.managers.IndexingStateManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingSitesImpl implements IndexingSitesService {

    private final PageProcessService pageProcessService;
    private final IndexingStateManager indexingStateManager;

    @Override
    public ResponseEntity<ApiResponse> startIndexing() {
        log.info("Запрос на старт индексации получен");

        if (!indexingStateManager.startIndexingManage()) {
            return ResponseEntity.ok(new ApiResponse(false, "Индексация уже запущена"));
        }

        new Thread(() -> {
            try {
                pageProcessService.indexingAllSites();
            } finally {
                indexingStateManager.stopIndexingManage();
            }
        }).start();

        return ResponseEntity.ok(new ApiResponse(true));
    }

    @Override
    public ResponseEntity<ApiResponse> stopIndexing() {
        if (!indexingStateManager.stopIndexingManage()) {
            return ResponseEntity.ok(new ApiResponse(false, "Индексация не запущена"));
        }

        pageProcessService.stopIndexingNow();
        log.info("Остановка индексации");
        return ResponseEntity.ok(new ApiResponse(true));
    }

    @Override
    public ResponseEntity<ApiResponse> indexPage(String path) {
        try {
            pageProcessService.reindexSinglePage(path);
            return ResponseEntity.ok(new ApiResponse(true));
        } catch (IllegalArgumentException e) {
            log.error("Ошибка обработки URL: {}", e.getMessage());
            return ResponseEntity.ok(new ApiResponse(false, e.getMessage()));
        } catch (Exception e) {
            log.error("Ошибка при реиндексации страницы", e);
            return ResponseEntity.ok(new ApiResponse(false, "Ошибка обработки запроса"));
        }
    }
}
