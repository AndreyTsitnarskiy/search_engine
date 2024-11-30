package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.SiteEntity;
import searchengine.repository.SiteRepository;
import searchengine.services.indexing.interfaces.PageProcessService;
import searchengine.services.indexing.interfaces.SIteProcessService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiteProcessServiceImpl implements SIteProcessService {

    private final SiteRepository siteRepository;
    private final PageProcessService pageProcessService;

    @Override
    @Transactional
    public void parseSites(List<SiteEntity> siteUrls) {
        siteUrls.forEach(site -> {
            CompletableFuture.runAsync(() -> parseSite(site)); // запуск в отдельном потоке
        });
    }

    @Override
    public void parseSite(SiteEntity siteEntity) {
        siteRepository.save(siteEntity);
        try {
            log.info("START PARSING ALL PAGES SITE " + siteEntity.getUrl() + " " + siteEntity.getName());
            pageProcessService.parsePage(siteEntity.getUrl(), siteEntity);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
