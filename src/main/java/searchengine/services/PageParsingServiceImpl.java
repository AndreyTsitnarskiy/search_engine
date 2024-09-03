package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.services.interfaces.PageParsingService;
import searchengine.utility.ProjectParameters;

import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class PageParsingServiceImpl implements PageParsingService {

    private final PageRepository pageRepository;
    private final ProjectParameters projectParameters;

    @Override
    public void parsePage(String pageUrl, SiteEntity siteEntity){
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        forkJoinPool.invoke(new PageTask(pageUrl, siteEntity, this, projectParameters));
    }

    public void savePageEntity(PageEntity pageEntity){
        pageRepository.save(pageEntity);
    }
}
