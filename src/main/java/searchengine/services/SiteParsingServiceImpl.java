package searchengine.services;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class SiteParsingServiceImpl implements SiteParsingService {

    private final SiteRepository siteRepository;
    private final PageParsingService pageParsingService;

    @Override
    @Transactional
    public void parseSites(List<SiteEntity> siteUrls) {
        siteUrls.forEach(this::parseSite);
    }

    @Override
    public void parseSite(SiteEntity siteEntity) {
        // Сохраняем сайт в БД перед началом парсинга
        siteRepository.save(siteEntity);

        try {
            log.info("START PARSING ALL PAGES SITE " + siteEntity.getUrl() + " " + siteEntity.getName());
            // Парсим главную страницу сайта и последующие страницы рекурсивно
            pageParsingService.parsePage(siteEntity.getUrl(), siteEntity);

            // Обновляем статус сайта на "INDEXED"
            //siteEntity.setStatuses(Statuses.INDEXED);
        } catch (Exception e) {
            // В случае ошибки устанавливаем статус "FAILED"
            siteEntity.setStatuses(Statuses.FAILED);
            siteEntity.setLastError(e.getMessage());
            e.printStackTrace();
        } finally {
            log.info("FINELLY BLOCK " + siteEntity.getUrl());
            // Сохраняем обновленный статус и ошибку (если есть)
            siteRepository.save(siteEntity);
        }
    }
}
