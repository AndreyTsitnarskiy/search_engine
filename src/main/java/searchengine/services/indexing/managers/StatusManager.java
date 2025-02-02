package searchengine.services.indexing.managers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.entity.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class StatusManager {

    private final Map<Integer, Boolean> siteErrorMap = new ConcurrentHashMap<>();
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    public void initializeSite(SiteEntity siteEntity) {
        siteEntity.setStatus(Status.INDEXING);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
        siteErrorMap.put(siteEntity.getId(), false);
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
        siteRepository.updateSiteStatusAndLastError(siteEntity.getId(), Status.FAILED, LocalDateTime.now(), lastError);
        siteErrorMap.put(siteEntity.getId(), true);
    }

    @Transactional
    public void updateStatusSiteIndexing(SiteEntity siteEntity) {
        siteRepository.updateSiteStatus(siteEntity.getId(), Status.INDEXING, LocalDateTime.now());
        siteErrorMap.put(siteEntity.getId(), false);
    }

    @Transactional
    public void updateStatusIndexed(SiteEntity siteEntity){
        siteEntity.setStatus(Status.INDEXED);
        siteRepository.save(siteEntity);
    }

    public boolean hasSiteErrors(SiteEntity siteEntity) {
        return siteErrorMap.get(siteEntity.getId()) == true;
    }

    public void clearSiteState(int siteId) {
        siteErrorMap.remove(siteId);
    }
}
