package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.repository.PageRepository;
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
    private final PropertiesProject projectParameters;
    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();

    @Override
    public void parsePage(String pageUrl, Document document, SiteEntity siteEntity) {
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        try {
            forkJoinPool.invoke(new SiteIndexingTask(pageUrl, siteEntity, projectParameters, this));
        } catch (Exception e) {
            log.info("ОШИБКА ");
            e.printStackTrace();
        } finally {
            forkJoinPool.shutdown();
            log.info("ForkJoinPool завершён.");
        }
    }

    public boolean isUrlVisited(String url) {
        return !visitedUrls.add(url);
    }

    @Transactional
    public void processPage(String url, Document document, SiteEntity siteEntity) {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(url);
        pageEntity.setSite(siteEntity);
        pageEntity.setContent(document.html());
        pageEntity.setCode(200);
        pageRepository.save(pageEntity);
        log.info("Saved page: " + url);
    }
}
