package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.entity.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.indexing.interfaces.StatusManager;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class StatusManagerImpl implements StatusManager {

    private final Map<Integer, Boolean> siteErrorMap = new ConcurrentHashMap<>();
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    @Override
    public void initializeSite(int siteId) {
        siteErrorMap.put(siteId, false);
    }

    @Override
    @Transactional
    public void updateStatusPage(SiteEntity siteEntity, String url, int code) {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(url);
        pageEntity.setCode(code);
        pageEntity.setSite(siteEntity);
        pageRepository.save(pageEntity);
    }

    @Override
    @Transactional
    public void updateStatusSiteFailed(SiteEntity siteEntity, String lastError) {
        siteRepository.updateSiteStatusAndLastError(siteEntity.getId(), Status.INDEXING, LocalDateTime.now(), lastError);
        siteErrorMap.put(siteEntity.getId(), true);
    }

    @Override
    @Transactional
    public void updateStatusSiteIndexing(SiteEntity siteEntity) {
        siteRepository.updateSiteStatus(siteEntity.getId(), Status.INDEXING, LocalDateTime.now());
        siteErrorMap.put(siteEntity.getId(), false);
    }

    @Override
    public boolean checkConditionStatusSite(SiteEntity siteEntity) {
        return siteErrorMap.get(siteEntity.getId()) == true;
    }

    @Override
    public void clearSiteState(int siteId) {
        siteErrorMap.remove(siteId);
    }
}
