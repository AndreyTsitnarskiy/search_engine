package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.SiteEntity;
import searchengine.entity.Status;
import searchengine.repository.SiteRepository;
import searchengine.services.indexing.interfaces.PageProcessService;
import searchengine.services.indexing.interfaces.SiteProcessService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiteProcessServiceImpl implements SiteProcessService {

    private final SiteRepository siteRepository;
    private final PageProcessService pageProcessService;

    @Transactional
    public void parseSites(List<SiteEntity> siteUrls) {
        List<CompletableFuture<Void>> futures = siteUrls.stream()
                .map(site -> CompletableFuture.runAsync(() -> parseSite(site)))
                .collect(Collectors.toList());
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    @Transactional
    public void parseSite(SiteEntity siteEntity) {
        try {
            siteEntity.setStatus(Status.INDEXING);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);

            pageProcessService.initializeSite(siteEntity.getId()); // Инициализация статуса
            log.info("START PARSING: " + siteEntity.getUrl());

            pageProcessService.parsePage(siteEntity.getUrl(), new Document(siteEntity.getUrl()), siteEntity);
            if(siteEntity.getStatus() != Status.FAILED){
                siteEntity.setStatus(Status.INDEXED);
                siteRepository.save(siteEntity);
            }
            pageProcessService.clearSiteState(siteEntity.getId());
        } catch (Exception e) {
            log.error("Error indexing site {}", siteEntity.getUrl(), e);
            siteEntity.setStatus(Status.FAILED);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteEntity.setLastError(e.getMessage());
            siteRepository.save(siteEntity);
        }
    }
}
