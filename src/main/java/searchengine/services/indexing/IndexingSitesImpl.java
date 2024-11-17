package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.response.IndexingRequest;
import searchengine.dto.response.IndexingResponse;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.entity.Statuses;
import searchengine.exeptions.BadRequestException;
import searchengine.exeptions.NotFoundException;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.lemmas.LemmaService;
import searchengine.utility.ConnectionUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingSitesImpl implements IndexingSitesService {

    private final SiteRepository siteRepository;
    private final IndexRepository indexRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    private final LemmaService lemmaService;
    private final SitesList sitesList;
    private final ConnectionUtils connectionUtils;


    @Override
    public IndexingResponse startIndexing() {
        log.info("Start indexing");
        if(existsIndexingSite()) {
            log.warn("Indexing already start");
            throw new BadRequestException("Индексация уже запущена");
        }

        deleteSites();

        for(searchengine.config.Site site : sitesList.getSites()) {
            String url = site.getUrl();
            log.info("Save site: {}", url);
            siteRepository.save(SiteEntity.builder()
                    .name(site.getName())
                    .status(Statuses.INDEXING)
                    .url(url.toLowerCase())
                    .statusTime(LocalDateTime.now())
                    .build());
        }

        for(SiteEntity siteEntity: siteRepository.findAll()){
            log.info("Start indexing: {}", siteEntity);
            runParser(siteEntity.getId(), "/");
        }
        return new IndexingResponse();
    }

    @Override
    public IndexingResponse stopIndexing() {
        log.info("Stop Indexing");
        if(!existsIndexingSite()){
            log.warn("Indexing not run");
            throw  new BadRequestException("Инедксация не запущена");
        }
        siteRepository.findAllByStatus(Statuses.INDEXING).forEach(site -> {
            site.setLastError("Индексация остановлена");
            site.setStatus(Statuses.FAILED);
            siteRepository.save(site);
        });
        return new IndexingResponse();
    }

    @Override
    public IndexingResponse singlePageIndexing(IndexingRequest indexingRequest) {
        String singleRequestUrl = indexingRequest.url();
        log.info("Index page: {}", singleRequestUrl);
        String siteUrl = "";
        String path = "/";
        try {
            URL url = new URL(singleRequestUrl);
            siteUrl = url.getProtocol() + "://" + url.getHost();
            path = url.getPath();
        } catch (MalformedURLException e){
            log.error("URL parser error", e);
        }

        path = path.trim();
        path = path.isBlank() ? "/" : path;

        Optional<SiteEntity> optionalSiteEntity = siteRepository.findByUrl(siteUrl);

        if(optionalSiteEntity.isPresent()) {
            SiteEntity siteEntity = optionalSiteEntity.get();
            if(!siteEntity.getStatus().equals(Statuses.INDEXED)) {
                log.warn("Site in not INDEXING");
                throw new BadRequestException("Сайт не был индексирован");
            }
            indexing(siteEntity.getId());
            deletePage(siteEntity, path);
            runParser(siteEntity.getId(), path);
            return new IndexingResponse();
        } else {
            log.warn("Site not found: {}", siteUrl);
            throw new NotFoundException("Данная страница находится за пределами сайтов");
        }
    }

    private void runParser(Integer siteId, String path){
        new PageTask(siteId, path, siteRepository, pageRepository, lemmaService, connectionUtils, true).fork();
    }

    private void deletePage(SiteEntity siteEntity, String path){
        log.info("Delete page {} for site {}", path, siteEntity);
        Optional<PageEntity> optionalPageEntity = pageRepository.findBySiteAndPath(siteEntity, path);
        optionalPageEntity.ifPresent(pageRepository::delete);
    }

    private void indexing(Integer siteId){
        SiteEntity siteEntity = siteRepository
                .findById(siteId)
                .orElseThrow(() -> new IllegalStateException("Site not found"));
        siteEntity.setStatus(Statuses.INDEXING);
        siteRepository.save(siteEntity);
        log.info("Site indexing: {}", siteEntity);
    }

    private void deleteSites() {
        log.info("Delete all sites");
        indexRepository.deleteAllInBatch();
        lemmaRepository.deleteAllInBatch();
        pageRepository.deleteAllInBatch();
        siteRepository.deleteAllInBatch();
    }

    private boolean existsIndexingSite() {
        return siteRepository.existsByStatus(Statuses.INDEXING);
    }
}
