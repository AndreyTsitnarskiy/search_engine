package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.IndexingStateManager;
import searchengine.dto.response.ApiResponse;
import searchengine.services.indexing.interfaces.IndexingSitesService;
import searchengine.services.indexing.interfaces.PageProcessService;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingSitesImpl implements IndexingSitesService {

    private final PageProcessService pageProcessService;
    private final IndexingStateManager indexingStateManager;

    @Override
    public ResponseEntity<ApiResponse> startIndexing() {
        if (!indexingStateManager.startIndexing()) {
            log.info("Индексация уже запущена");
            return ResponseEntity.ok(ApiResponse.failure("Индексация уже запущена"));
        }

        try {
            pageProcessService.indexingAllSites();
            log.info("Индексация всех сайтов запущена");
            return ResponseEntity.ok(ApiResponse.success("Индексация начата"));
        } catch (Exception e) {
            log.error("Ошибка при запуске индексации: {}", e.getMessage());
            indexingStateManager.stopIndexing();
            return ResponseEntity.ok(ApiResponse.failure("Ошибка при запуске индексации"));
        }
    }

    @Override
    public ResponseEntity<ApiResponse> indexPage(String path) {
        if (!indexingStateManager.isIndexing()) {
            log.warn("Индексация не запущена, реиндексация отдельной страницы невозможна");
            return ResponseEntity.ok(ApiResponse.failure("Индексация не запущена"));
        }

        log.info("Реиндексация страницы: {}", path);
        try {
            pageProcessService.reindexSinglePage(path);
            return ResponseEntity.ok(ApiResponse.success("Реиндексация страницы завершена"));
        } catch (Exception e) {
            log.error("Ошибка при реиндексации страницы: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.failure("Ошибка при реиндексации страницы"));
        }
    }

    @Override
    public ResponseEntity<ApiResponse> stopIndexing() {
        if (!indexingStateManager.isIndexing()) {
            log.warn("Индексация не запущена, остановка невозможна");
            return ResponseEntity.ok(ApiResponse.failure("Индексация не запущена"));
        }
        pageProcessService.stopIndexing();
        indexingStateManager.stopIndexing();
        log.info("Индексация остановлена");
        return ResponseEntity.ok(ApiResponse.success("Индексация остановлена"));
    }
}
