package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.indexing.interfaces.LemmaProcessService;
import searchengine.services.indexing.interfaces.PageProcessService;
import searchengine.utility.PropertiesProject;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageProcessServiceImpl implements PageProcessService {

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaProcessService lemmaProcessService;
    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();

    @Override
    public void parseSite(SiteEntity siteEntity) {
        ForkJoinPool.commonPool().invoke(new PageTask(siteEntity.getUrl(), siteEntity, this));
    }

    @Override
    public void indexSinglePage(String url) {
        ForkJoinPool.commonPool().invoke(new PageTask(url, null, this));
    }

    @Transactional
    @Override
    public void deleteAllSiteAndPages() {
        lemmaProcessService.deleteAllLemmasAndIndexes();
        pageRepository.deleteAllPages();
        siteRepository.deleteAllSites();
    }

    public boolean isUrlVisited(String url) {
        return !visitedUrls.add(url);
    }

    @Transactional
    public void savePage(String url, String content, int code, SiteEntity siteEntity) {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(url);
        pageEntity.setContent(content);
        pageEntity.setCode(code);
        pageEntity.setSite(siteEntity);
        pageRepository.save(pageEntity);
    }
}
