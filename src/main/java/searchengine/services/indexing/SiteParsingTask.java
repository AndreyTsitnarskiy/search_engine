package searchengine.services.indexing;

import lombok.extern.slf4j.Slf4j;
import searchengine.entity.SiteEntity;
import searchengine.services.indexing.interfaces.SiteProcessService;

import java.util.concurrent.RecursiveAction;

@Slf4j
public class SiteParsingTask extends RecursiveAction {
    private final SiteEntity siteEntity;
    private final SiteProcessService siteProcessService;

    public SiteParsingTask(SiteEntity siteEntity, SiteProcessService siteProcessService) {
        this.siteEntity = siteEntity;
        this.siteProcessService = siteProcessService;
    }

    @Override
    protected void compute() {
        try {
            siteProcessService.parseSite(siteEntity);
        } catch (Exception e) {
            log.error("Error parsing site {}", siteEntity.getUrl(), e);
        }
    }
}
