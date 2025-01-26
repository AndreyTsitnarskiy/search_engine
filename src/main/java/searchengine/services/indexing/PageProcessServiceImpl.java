package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.entity.SiteEntity;
import searchengine.entity.Status;
import searchengine.services.indexing.interfaces.PageProcessService;
import searchengine.utility.PropertiesProject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageProcessServiceImpl implements PageProcessService {

    private final PropertiesProject projectParameters;
    private final StatusManager statusManager;
    private final VisitedUrlsManager visitedUrlsManager;
    private final RepositoryManager repositoryManager;
    private final ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();

    @Override
    public void indexingAllSites() {
        repositoryManager.truncateAllSiteAndPages();
        List<SiteEntity> siteEntityList = repositoryManager.getListSiteEntity();
        try {
            parseSites(siteEntityList);
            shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void parseSites(List<SiteEntity> sites) {
        List<SiteTask> siteTasks = new ArrayList<>();

        for (SiteEntity siteEntity : sites) {
            statusManager.initializeSite(siteEntity);
            log.info("START PARSING: " + siteEntity.getUrl());
            siteTasks.add(new SiteTask(siteEntity.getUrl(),
                    siteEntity,
                    projectParameters,
                    this,
                    statusManager,
                    visitedUrlsManager,
                    repositoryManager));
            visitedUrlsManager.isUrlVisited(siteEntity.getUrl());
        }

        siteTasks.forEach(ForkJoinTask::fork);
        siteTasks.forEach(ForkJoinTask::join);

        for (SiteEntity siteEntity : sites) {
            if (siteEntity.getStatus() != Status.FAILED) {
                statusManager.updateStatusIndexed(siteEntity);
            }
            visitedUrlsManager.clearUrls(siteEntity.getUrl());
            statusManager.clearSiteState(siteEntity.getId());
        }
    }

    public void shutdown() {
        forkJoinPool.shutdown();
        try {
            if (!forkJoinPool.awaitTermination(60, TimeUnit.SECONDS)) {
                forkJoinPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            forkJoinPool.shutdownNow();
        }
    }
}
