package searchengine.services.indexing;

import lombok.extern.slf4j.Slf4j;
import searchengine.entity.SiteEntity;
import searchengine.entity.Status;
import searchengine.repository.SiteRepository;
import searchengine.services.indexing.interfaces.PageProcessService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.RecursiveAction;

@Slf4j
public class SiteIndexingTask extends RecursiveAction {

    private final List<SiteEntity> sites;
    private final PageProcessService pageProcessService;
    private final SiteRepository siteRepository;

    public SiteIndexingTask(List<SiteEntity> sites, PageProcessService pageProcessService, SiteRepository siteRepository) {
        this.sites = sites;
        this.pageProcessService = pageProcessService;
        this.siteRepository = siteRepository;
    }

    @Override
    protected void compute() {
        List<SiteIndexingTask> subTasks = sites.stream().map(site ->
                new SiteIndexingTask(List.of(site), pageProcessService, siteRepository)
        ).toList();

        invokeAll(subTasks);

        subTasks.forEach(task -> {
            SiteEntity site = task.sites.get(0);
            try {
                pageProcessService.parseSite(site);
                site.setStatus(Status.INDEXED);
            } catch (Exception e) {
                log.error("Ошибка индексации сайта {}: {}", site.getUrl(), e.getMessage(), e);
                site.setStatus(Status.FAILED);
                site.setLastError(e.getMessage());
            } finally {
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
            }
        });
    }
}
