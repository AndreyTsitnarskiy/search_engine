package searchengine.services.indexing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.response.ApiResponse;
import searchengine.entity.*;
import searchengine.exceptions.SiteExceptions;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.utility.ConnectionUtil;
import searchengine.utility.LemmaExecute;
import searchengine.utility.PropertiesProject;
import searchengine.utility.ReworkString;

import javax.net.ssl.SSLHandshakeException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateExpiredException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingSitesImpl implements IndexingSitesService {

    private final SitesList sites;
    private ForkJoinPool forkJoinPool = new ForkJoinPool();
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
    private ReentrantLock lock = new ReentrantLock();

    @Getter
    private final PropertiesProject propertiesProject;
    private volatile boolean isIndexing = false;

    @Getter
    private Set<String> webPages;

    @Getter
    private ConcurrentMap<String, Status> siteStatusMap;
    private ConcurrentMap<Integer, Map<String, LemmaEntity>> lemmasMap;
    private ConcurrentMap<Integer, Set<IndexEntity>> indexMap;

    @Override
    public ResponseEntity<ApiResponse> startIndexing() {
        ApiResponse apiResponse = new ApiResponse();
        //deleteAllDataFromDatabase();
        if (isIndexing) {
            apiResponse.setResult(false);
            apiResponse.setMessageError("Indexing already started");
            log.info("Indexing already started");
        } else {
            new Thread(this::indexAll).start();
            apiResponse.setResult(true);
            log.info("Indexing started");
        }
        return ResponseEntity.ok(apiResponse);
    }

    @Override
    public ResponseEntity<ApiResponse> indexPage(String path) {
        log.info("INDEX PAGE: " + path);
        ApiResponse apiResponse = new ApiResponse();
        try {
            if (isPageBelongsToSiteSpecified(path)) {
                new Thread(() -> indexSinglePage(path)).start();
                log.info("Page indexed " + path);
                apiResponse.setResult(true);
            } else {
                apiResponse.setResult(false);
                apiResponse.setMessageError("Page is located outside the sites specified in the configuration file");
            }
        } catch (SiteExceptions siteExceptions) {
            apiResponse.setResult(false);
            apiResponse.setMessageError("Path incorrect");
        }
        log.info("END INDEX PAGE OFF: " + path);
        return ResponseEntity.ok(apiResponse);
    }

    @Override
    public ResponseEntity<ApiResponse> stopIndexing() {
        ApiResponse apiResponse = new ApiResponse();
        if (isIndexing) {
            shutdown();
            apiResponse.setResult(true);
            log.info("Indexing stopped");
            saveDataFromMapsToDatabase();
            log.info("Data saved");
        } else {
            apiResponse.setResult(false);
            apiResponse.setMessageError("Indexing not started");
        }
        return ResponseEntity.ok(apiResponse);
    }

    private void indexAll() {
        List<Site> allSiteConfig = sites.getSites();
        isIndexing = true;
        forkJoinPool = new ForkJoinPool();
        lemmasMap = new ConcurrentHashMap<>();
        indexMap = new ConcurrentHashMap<>();
        webPages = Collections.synchronizedSet(new HashSet<>());
        siteStatusMap = new ConcurrentHashMap<>();
        for (Site site : allSiteConfig) {
            Thread thread = new Thread(() -> indexSingleSite(site));
            thread.setName(site.getName());
            thread.start();
        }
    }

    public void indexSinglePage(String pageUrl) {
        SiteEntity siteEntity = findOrCreateNewSiteEntity(pageUrl);
        Connection connection = ConnectionUtil.getConnection(pageUrl, propertiesProject.getUserAgent(), propertiesProject.getReferrer());
        Connection.Response response = ConnectionUtil.getResponse(connection);
        Document document = ConnectionUtil.getDocument(connection);
        String pathToSave = ReworkString.getPathToSave(pageUrl, siteEntity.getUrl());
        int httpStatusCode = response.statusCode();

        PageEntity deletePageEntity = deleteOldPageEntity(pathToSave, siteEntity);
        String html = "";
        PageEntity pageEntity = new PageEntity(siteEntity, pathToSave, httpStatusCode, html);
        if (httpStatusCode != 200) {
            savePageAndSiteStatusTime(pageEntity, siteEntity);
        } else {
            html = document.outerHtml();
            if (deletePageEntity != null) {
                reduceLemmaFrequenciesByOnePage(html, siteEntity.getId());
            }
            savePageAndSiteStatusTime(pageEntity, siteEntity);
            log.info("Page indexed: " + pathToSave);
            extractLemmas(pageEntity, siteEntity);
        }
        fixSiteStatusAfterSinglePageIndexed(siteEntity);
    }

    private PageEntity deleteOldPageEntity(String path, SiteEntity siteEntity) {
        PageEntity deletePageEntity = pageRepository.findPageEntityByPathAndSite(path, siteEntity);
        if (deletePageEntity == null) {
            return null;
        }
        pageRepository.delete(deletePageEntity);
        return deletePageEntity;
    }

    private SiteEntity findOrCreateNewSiteEntity(String url) {
        String siteUrlFromPageUrl = ReworkString.getStartPage(url);
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(siteUrlFromPageUrl);
        if (siteEntity == null) {
            siteEntity = createSiteToHandleSinglePage(siteUrlFromPageUrl);
        }
        return siteEntity;
    }

    private void reduceLemmaFrequenciesByOnePage(String html, int siteId) {
        Map<String, Integer> allUniquePageLemmas = getAllLemmasPage(html);
        lemmaRepository.reduceByOneLemmaFrequencies(siteId, allUniquePageLemmas.keySet());
        lemmaRepository.deleteLemmasWithNoFrequencies(siteId);
    }

    private SiteEntity createSiteToHandleSinglePage(String siteHomePageToSave) {
        SiteEntity siteEntity = new SiteEntity();
        String currentSiteHomePage;
        for (Site site : sites.getSites()) {
            currentSiteHomePage = ReworkString.getStartPage(site.getUrl());
            if (siteHomePageToSave.equalsIgnoreCase(currentSiteHomePage)) {
                siteEntity = createAndPrepareSiteForIndexing(site);
                break;
            }
        }
        return siteEntity;
    }

    private void indexSingleSite(Site site) {
        try {
            log.info("Indexing started for site: {}", site.getName());
            long startTime = System.currentTimeMillis();

            CrawlerTask pageParse = initCollectionsForSiteAndCreateMainPageSiteParser(site);
            forkJoinPool.invoke(pageParse);
            int siteId = getSiteId(site);
            processLemmaSaveBatchData(siteId);
            markSiteAsIndexed(site);
            long endTime = System.currentTimeMillis();
            log.info("Indexing completed for site: {}. Time taken: {} ms", site.getName(), (endTime - startTime));
        } catch (Exception exception) {
            log.warn("Indexing FAILED for site: {} due to {}", site.getName(), exception.getMessage());
            fixSiteIndexingError(site, exception);
            clearLemmasAndIndexTable(site);
        } finally {
            markIndexingCompletionIfApplicable();
        }
    }

    private Map<String, Integer> getAllLemmasPage(String html) {
        Document document = ConnectionUtil.parse(html);
        String title = document.title();
        String body = document.body().text();

        Map<String, Integer> titleLemmas = LemmaExecute.getLemmaMap(title);
        Map<String, Integer> bodyLemmas = LemmaExecute.getLemmaMap(body);

        return Stream.concat(titleLemmas.entrySet().stream(), bodyLemmas.entrySet().stream())
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.summingInt(Map.Entry::getValue)));
    }

    public void extractLemmas(PageEntity pageEntity, SiteEntity siteEntity) {
        Map<String, Integer> lemmaEntityHashMap = getAllLemmasPage(pageEntity.getContent());

        if(checkCountSizeBatch()){
            saveDataFromMapsToDatabase();
        }

        for (String lemmas : lemmaEntityHashMap.keySet()) {
            Map<String, LemmaEntity> allLemmasBySiteId = lemmasMap.get(siteEntity.getId());
            LemmaEntity lemmaEntity = allLemmasBySiteId.get(lemmas);

            if (lemmaEntity == null) {
                lemmaEntity = new LemmaEntity();
                lemmaEntity.setLemma(lemmas);
                lemmaEntity.setFrequency(1);
                lemmaEntity.setSite(siteEntity);
                lemmasMap.get(siteEntity.getId()).put(lemmas, lemmaEntity);
            } else {
                int count = allLemmasBySiteId.get(lemmas).getFrequency();
                lemmasMap.get(siteEntity.getId()).get(lemmas).setFrequency(count + 1);
            }

            float lemmaRank = (float) lemmaEntityHashMap.get(lemmas);
            IndexEntity indexEntity = new IndexEntity(pageEntity, lemmaEntity, lemmaRank);
            indexMap.get(siteEntity.getId()).add(indexEntity);
        }
    }

    public void savePageAndSiteStatusTime(PageEntity pageEntity, SiteEntity siteEntity) {
        if (!forkJoinPool.isTerminating()
                && !forkJoinPool.isTerminated()
                && !siteStatusMap.get(siteEntity.getUrl()).equals(Status.FAILED)) {
            savePageAndSite(pageEntity, siteEntity);
        }
    }

    public void savePageAndSite(PageEntity pageEntity, SiteEntity siteEntity) {
        pageRepository.save(pageEntity);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
    }

    @Transactional
    private void saveDataFromMapsToDatabase() {
        try {
            lock.lock();
            for (Site site : sites.getSites()) {
                int siteId = getSiteId(site);
                processLemmaSaveBatchData(siteId);
            }
        } catch (Exception exception) {
            log.warn("Data saving FAILED due to " + exception);
        } finally {
            lock.unlock();
        }
    }

    private boolean checkCountSizeBatch(){
        return lemmasMap.values().stream().mapToInt(Map::size).sum() > 2000;
    }

    private int getSiteId(Site site){
        String url = ReworkString.getStartPage(site.getUrl());
        return siteRepository.findSiteEntityByUrl(url).getId();
    }

    private void processLemmaSaveBatchData(int siteId) {
        Map<String, LemmaEntity> lemmaEntityMap = lemmasMap.get(siteId);
        if (lemmaEntityMap == null || lemmaEntityMap.isEmpty()) {
            return;
        }
        Set<String> lemmas = lemmaEntityMap.keySet();
        List<LemmaEntity> inRepositoryLemma = lemmaRepository.findBySiteIdAndLemmaIn(siteId, lemmas);
        List<LemmaEntity> lemmasToSave = new ArrayList<>();

        for (LemmaEntity existingLemma : inRepositoryLemma) {
            LemmaEntity newLemma = lemmaEntityMap.get(existingLemma.getLemma());
            if (newLemma != null) {
                existingLemma.setFrequency(existingLemma.getFrequency() + newLemma.getFrequency());
                lemmasToSave.add(existingLemma);
                lemmaEntityMap.remove(existingLemma.getLemma());
            }
        }

        lemmasToSave.addAll(lemmaEntityMap.values());
        lemmaRepository.saveAll(lemmasToSave);
        processIndexesSaveBatchData(siteId);
        lemmasMap.get(siteId).clear();
    }

    private void processIndexesSaveBatchData(int siteId) {
        Set<IndexEntity> indexEntitySet = indexMap.get(siteId);
        indexRepository.saveAll(indexEntitySet);
        indexMap.get(siteId).clear();
    }

    private void clearLemmasAndIndexTable(Site site) {
        String url = ReworkString.getStartPage(site.getUrl());
        int siteEntityId = siteRepository.findSiteEntityByUrl(url).getId();
        lemmasMap.get(siteEntityId).clear();
        indexMap.get(siteEntityId).clear();
    }

    private CrawlerTask initCollectionsForSiteAndCreateMainPageSiteParser(Site siteToHandle) {
        SiteEntity siteEntity = createAndPrepareSiteForIndexing(siteToHandle);
        siteStatusMap.put(siteEntity.getUrl(), Status.INDEXING);
        Map<String, LemmaEntity> lemmaEntityMap = new HashMap<>();
        lemmasMap.put(siteEntity.getId(), lemmaEntityMap);
        Set<IndexEntity> indexEntitySet = new HashSet<>();
        indexMap.put(siteEntity.getId(), indexEntitySet);
        String siteHomePage = siteEntity.getUrl();
        webPages.add(siteHomePage);
        return new CrawlerTask(this, siteEntity, siteHomePage);
    }

    private SiteEntity createAndPrepareSiteForIndexing(Site site) {
        String homePage = ReworkString.getStartPage(site.getUrl());
        SiteEntity oldSiteEntity = siteRepository.findSiteEntityByUrl(homePage);
        if (oldSiteEntity != null) {
            oldSiteEntity.setStatus(Status.INDEXING);
            oldSiteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(oldSiteEntity);
            siteRepository.deleteSiteEntityByUrl(homePage);
        }
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setStatus(Status.INDEXING);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteEntity.setUrl(homePage);
        siteEntity.setName(site.getName());
        return siteRepository.save(siteEntity);
    }

    private void markSiteAsIndexed(Site site) {
        String homePage = ReworkString.getStartPage(site.getUrl());
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(homePage);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteEntity.setStatus(Status.INDEXED);
        siteRepository.save(siteEntity);
    }

    private void markIndexingCompletionIfApplicable() {
        List<SiteEntity> allSites = siteRepository.findAll();
        for (SiteEntity site : allSites) {
            if (site.getStatus().equals(Status.INDEXING)) {
                return;
            }
        }
        isIndexing = false;
    }

    private void fixSiteIndexingError(Site site, Exception e) {
        String error = getErrorMessage(e);
        String homePage = ReworkString.getStartPage(site.getUrl());
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(homePage);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteEntity.setStatus(Status.FAILED);
        siteEntity.setLastError(error);
        siteRepository.save(siteEntity);
    }

    private String getErrorMessage(Exception e) {
        if (e instanceof CancellationException || e instanceof InterruptedException) {
            return propertiesProject.getInterruptedByUserMessage();
        } else if (e instanceof CertificateExpiredException || e instanceof SSLHandshakeException
                || e instanceof CertPathValidatorException) {
            return propertiesProject.getCertificateError();
        } else {
            return propertiesProject.getUnknownError() + " (" + e + ")";
        }
    }

    private boolean isPageBelongsToSiteSpecified(String pageUrl) {
        if (pageUrl == null || pageUrl.isEmpty()) {
            return false;
        }
        List<Site> siteList = sites.getSites();
        for (Site site : siteList) {
            String siteHomePage = ReworkString.getStartPage(site.getUrl());
            String passedHomePage = ReworkString.getStartPage(pageUrl);
            if (passedHomePage.equalsIgnoreCase(siteHomePage)) {
                return true;
            }
        }
        return false;
    }

    private void fixSiteStatusAfterSinglePageIndexed(SiteEntity site) {
        site.setStatus(Status.INDEXED);
        siteRepository.save(site);
    }

    private void shutdown() {
        forkJoinPool.shutdownNow();
        try {
            if (!forkJoinPool.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("ForkJoinPool did not terminate properly");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
