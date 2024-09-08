package searchengine.services;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.entity.SiteEntity;
import searchengine.entity.Statuses;
import searchengine.repository.SiteRepository;
import searchengine.services.interfaces.PageParsingService;
import searchengine.services.interfaces.SiteParsingService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiteParsingServiceImpl implements SiteParsingService {

    private final SiteRepository siteRepository;
    private final PageParsingService pageParsingService;

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
            pageParsingService.parsePage(siteEntity.getUrl(), siteEntity);

        } catch (Exception e) {
            updateStatus(Statuses.FAILED, siteEntity.getId(), e.getMessage());
            e.printStackTrace();
        } finally {
            log.info("FINELLY BLOCK " + siteEntity.getUrl());
            if(siteEntity.getStatuses() != Statuses.FAILED) {
                updateStatus(Statuses.INDEXED, siteEntity.getId());
                siteRepository.save(siteEntity);
            }
        }
    }

    private void updateStatus(Statuses statuses, long idSiteEntity){
        SiteEntity uPsiteEntity = siteRepository.findById(idSiteEntity).orElseThrow(EntityNotFoundException::new);
        uPsiteEntity.setStatuses(statuses);
        siteRepository.save(uPsiteEntity);
    }

    private void updateStatus(Statuses statuses, long idSiteEntity, String textError){
        SiteEntity uPsiteEntity = siteRepository.findById(idSiteEntity).orElseThrow(EntityNotFoundException::new);
        uPsiteEntity.setStatuses(statuses);
        uPsiteEntity.setLastError(textError);
        siteRepository.save(uPsiteEntity);
    }
}
