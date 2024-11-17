package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.UnsupportedMimeTypeException;
import searchengine.dto.PageInfo;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.entity.Statuses;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.lemmas.LemmaService;
import searchengine.utility.ConnectionUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class PageTask extends RecursiveAction {

    private final Integer siteId;
    private final String path;
    private final transient SiteRepository siteRepository;
    private final transient PageRepository pageRepository;
    private final transient LemmaService lemmaService;
    private final transient ConnectionUtils connectionUtils;
    private final boolean isAction;

    @Override
    protected void compute() {
        if (isNotFailed(siteId) && isNotVisited(siteId, path)) {
            try {
                updateStatusTime(siteId);
                Optional<PageEntity> optionalPageEntity = savePageEntity(siteId, path);

                if (optionalPageEntity.isPresent()) {
                    PageEntity pageEntity = optionalPageEntity.get();

                    if (pageEntity.getCode() < 400) {
                        lemmaService.findAndSave(pageEntity);
                    }

                    Set<ForkJoinTask<Void>> tasks = connectionUtils.getPaths(pageEntity.getContent()).stream()
                            .map(pathFromPage -> new PageTask(siteId, pathFromPage, siteRepository, pageRepository, lemmaService, connectionUtils, false)
                                    .fork())
                            .collect(Collectors.toSet());
                    tasks.forEach(ForkJoinTask::join);
                    if (isAction && isNotFailed(siteId)) {
                        lemmaService.updateLemmasFrequency(siteId);
                        indexed(siteId);
                    }
                }
            } catch (UnsupportedMimeTypeException e) {
                log.warn("UnsupportedMimeTypeException", e);
            } catch (InterruptedException e) {
                log.warn("Interrupted!", e);
                failed(siteId, "Ошибка парсинга: Interrupted");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Parser exception", e);
                failed(siteId, "Ошибка парсинга URL: " + getPersistSite(siteId).getUrl() + path);
            }
        }
    }

    public Optional<PageEntity> savePageEntity(Integer siteId, String path) throws IOException, InterruptedException {
        synchronized (pageRepository) {
            SiteEntity site = getPersistSite(siteId);
            PageInfo pageInfo = connectionUtils.getPageInfo(site.getUrl() + path);
            if (isNotVisited(siteId, path)) {
                return Optional.of(pageRepository.save(PageEntity.builder()
                        .path(path)
                        .site(site)
                        .code(pageInfo.getStatusCode())
                        .content(pageInfo.getContent())
                        .build()));
            } else {
                return Optional.empty();
            }
        }
    }

    private void failed(Integer siteId, String error) {
        log.warn("Failed indexing site with id {}: {}", siteId, error);
        SiteEntity persistSite = getPersistSite(siteId);
        persistSite.setLastError(error);
        persistSite.setStatus(Statuses.FAILED);
        siteRepository.save(persistSite);
    }


    private void indexed(Integer siteId) {
        SiteEntity persistSite = getPersistSite(siteId);
        persistSite.setStatusTime(LocalDateTime.now());
        persistSite.setStatus(Statuses.INDEXED);
        siteRepository.save(persistSite);
    }

    private void updateStatusTime(Integer siteId) {
        SiteEntity persistSite = getPersistSite(siteId);
        persistSite.setStatusTime(LocalDateTime.now());
        siteRepository.save(persistSite);
    }

    private SiteEntity getPersistSite(Integer siteId) {
        return siteRepository.findById(siteId).orElseThrow(() -> new IllegalStateException("Site not found"));
    }


    private boolean isNotFailed(Integer siteId) {
        return !siteRepository.existsByIdAndStatus(siteId, Statuses.FAILED);
    }

    private boolean isNotVisited(Integer siteId, String path) {
        return !pageRepository.existsBySiteAndPath(siteRepository.findById(siteId), path);
    }
}


