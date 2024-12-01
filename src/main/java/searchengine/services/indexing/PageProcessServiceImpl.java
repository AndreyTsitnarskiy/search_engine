package searchengine.services.indexing;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.entity.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.indexing.interfaces.PageProcessService;
import searchengine.utility.PropertiesProject;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageProcessServiceImpl implements PageProcessService {

    private static final ForkJoinPool FORK_JOIN_POOL = new ForkJoinPool();

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final PropertiesProject projectParameters;
    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Boolean> siteErrorMap = new ConcurrentHashMap<>(); // Ошибки сайтов
    //private final Map<Integer, AtomicInteger> activePagesMap = new ConcurrentHashMap<>(); // Счетчик активных страниц

    @Override
    public void parsePage(String pageUrl, Document document, SiteEntity siteEntity) {
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        try {
            FORK_JOIN_POOL.invoke(new SiteIndexingTask(pageUrl, siteEntity, projectParameters, this));
        } catch (Exception e) {
            log.info("ОШИБКА ");
            e.printStackTrace();
        } finally {
            forkJoinPool.shutdown();
            try {
                if (!forkJoinPool.awaitTermination(10, TimeUnit.MINUTES)) {
                    log.warn("ForkJoinPool did not terminate within the timeout.");
                }
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for ForkJoinPool to terminate", e);
                Thread.currentThread().interrupt();
            }
            log.info("ForkJoinPool завершён.");
        }
    }

    @Override
    public void clearSiteState(int siteId) {
        siteErrorMap.remove(siteId);
        //activePagesMap.remove(siteId);
        visitedUrls.removeIf(url -> url.startsWith(siteRepository.findById(siteId).get().getUrl()));
    }

    @Override
    public void initializeSite(int siteId) {
        siteErrorMap.put(siteId, false); // Изначально ошибок нет
        //activePagesMap.put(siteId, new AtomicInteger(0)); // Счетчик активных страниц
    }

/*    // Пометить сайт как содержащий ошибку
    public void markSiteAsFailed(int siteId) {
        siteErrorMap.put(siteId, true);
    }

    // Увеличение счетчика активных страниц
    public void incrementActivePages(int siteId) {
        activePagesMap.get(siteId).incrementAndGet();
    }

    // Уменьшение счетчика активных страниц
    public void decrementActivePagesAndUpdateStatus(SiteEntity siteEntity) {
        int remainingPages = activePagesMap.get(siteEntity.getId()).decrementAndGet();
        if (remainingPages == 0) { // Все страницы обработаны
            boolean hasErrors = siteErrorMap.get(siteEntity.getId());
            siteEntity.setStatus(hasErrors ? Status.FAILED : Status.INDEXING);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteEntity.setLastError(hasErrors ? "Some pages failed to process." : null);
            siteRepository.save(siteEntity); // Обновляем статус сайта в базе
            log.info("Site {} indexing completed. Final status: {}", siteEntity.getUrl(), siteEntity.getStatus());
        }
    }*/

    public boolean isUrlVisited(String url) {
        return !visitedUrls.add(url);
    }

    @Transactional
    public void processPage(String url, Document document, SiteEntity siteEntity) {
        //incrementActivePages(siteEntity.getId());
        try {
            PageEntity pageEntity = new PageEntity();
            pageEntity.setPath(url);
            pageEntity.setSite(siteEntity);
            pageEntity.setContent(document.html());
            pageEntity.setCode(200);
            pageRepository.save(pageEntity);
        } catch (DataIntegrityViolationException e) {
            log.info("Запись уже существует в базе данных для страницы " + siteEntity.getUrl() + url);
        } finally {
            //decrementActivePagesAndUpdateStatus(siteEntity); // Уменьшаем счетчик
        }
    }

    @Transactional
    public void updateStatusPage(SiteEntity siteEntity, String url, int code) {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(url);
        pageEntity.setCode(code);
        pageEntity.setSite(siteEntity);
        pageRepository.save(pageEntity);
    }

    @Transactional
    public void updateStatusSiteFailed(SiteEntity siteEntity, String lastError) {
        Optional<SiteEntity> optionalSiteEntity = siteRepository.findById(siteEntity.getId());
        if (optionalSiteEntity.isPresent()) {
            SiteEntity site = optionalSiteEntity.get();
            site.setStatus(Status.FAILED);
            site.setStatusTime(LocalDateTime.now());
            site.setLastError(lastError);
            siteRepository.save(siteEntity);
            siteErrorMap.put(siteEntity.getId(), true);
        } else {
            throw new EntityNotFoundException("Сайт не найден для изменения статуса на FAILED");
        }
    }

    public boolean checkConditionStatusSite(SiteEntity siteEntity) {
        return siteErrorMap.get(siteEntity.getId()) == true;
    }

    @Transactional
    public void updateStatusSiteIndexing(SiteEntity siteEntity) {
        Optional<SiteEntity> optionalSiteEntity = siteRepository.findById(siteEntity.getId());
        if (optionalSiteEntity.isPresent()) {
            SiteEntity site = optionalSiteEntity.get();
            site.setStatus(Status.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);
            siteErrorMap.put(siteEntity.getId(), false);
        } else {
            throw new EntityNotFoundException("Сайт не найден для изменения статуса на INDEXING");
        }
    }
}
