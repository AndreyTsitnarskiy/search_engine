package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.services.indexing.interfaces.PageProcessService;
import searchengine.utility.PropertiesProject;

import java.util.concurrent.ForkJoinPool;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageProcessServiceImpl implements PageProcessService {

    private final PageRepository pageRepository;
    private final PropertiesProject projectParameters;

    @Override
    public void parsePage(String pageUrl, SiteEntity siteEntity) {
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        try {
            //log.info("Активные потоки: " + forkJoinPool.getActiveThreadCount());
            //log.info("Запланированные задачи: " + forkJoinPool.getQueuedTaskCount());
            forkJoinPool.invoke(new SiteIndexingTask(pageUrl, siteEntity, projectParameters, this));
            //log.info("Все задачи завершены.");
        } finally {
            forkJoinPool.shutdown();
            log.info("ForkJoinPool завершён.");
        }
    }

    public void savePageEntity(PageEntity pageEntity){
        pageRepository.save(pageEntity);
    }
}
