package searchengine.services.managers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.entity.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class RepositoryManager {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public List<SiteEntity> getListSiteEntity() {
        List<Site> sites = sitesList.getSites();
        List<SiteEntity> siteEntityList = new ArrayList<>();
        for (Site site : sites) {
            SiteEntity siteEntity = new SiteEntity();
            siteEntity.setName(site.getName());
            siteEntity.setUrl(site.getUrl());
            siteEntity.setStatusTime(LocalDateTime.now());
            siteEntity.setStatus(Status.INDEXING);
            siteEntityList.add(siteEntity);
            siteRepository.save(siteEntity);
        }
        return siteEntityList;
    }

    public List<SiteEntity> getAllSitesFromRepository(){
        return siteRepository.findAll();
    }

    @Transactional
    public PageEntity processPage(String url, Document document, SiteEntity siteEntity) {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(url);
        pageEntity.setSite(siteEntity);
        pageEntity.setContent(document.html());
        pageEntity.setCode(200);
        pageRepository.save(pageEntity);
        return pageEntity;
    }

    @Transactional
    public LemmaEntity processAndSaveLemma(SiteEntity siteEntity, String lemma) {
        String key = siteEntity.getId() + ":" + lemma;
        locks.putIfAbsent(key, new Object());

        synchronized (locks.get(key)) {
            try {
                LemmaEntity lemmaEntity = lemmaRepository.findBySite_IdAndLemma(siteEntity.getId(), lemma)
                        .orElseGet(() -> {
                            LemmaEntity newLemma = new LemmaEntity();
                            newLemma.setSite(siteEntity);
                            newLemma.setLemma(lemma);
                            newLemma.setFrequency(0);
                            return lemmaRepository.save(newLemma);
                        });

                lemmaRepository.incrementFrequency(siteEntity.getId(), lemma);
                return lemmaEntity;
            } finally {
                locks.remove(key);
            }
        }
    }

    @Transactional
    public void saveIndex(PageEntity pageEntity, LemmaEntity lemmaEntity, float freq) {
        IndexEntity indexEntity = new IndexEntity();
        indexEntity.setRank(freq);
        indexEntity.setLemma(lemmaEntity);
        indexEntity.setPage(pageEntity);
        indexRepository.save(indexEntity);
    }

    public Optional<SiteEntity> findSiteByUrl(String url) {
        return siteRepository.findAll().stream()
                .filter(site -> url.startsWith(site.getUrl()))
                .findFirst();
    }

    @Transactional
    public void deletePageAndAssociatedData(String url, SiteEntity siteEntity) {
        String uri = url.substring(siteEntity.getUrl().length());
        Optional<PageEntity> pageEntityOpt = pageRepository.findBySiteAndPath(siteEntity.getId(), uri);

        if (pageEntityOpt.isPresent()) {
            PageEntity pageEntity = pageEntityOpt.get();
            indexRepository.deleteAllByPage(pageEntity.getId());
            List<LemmaEntity> lemmasToDecrement = lemmaRepository.findUnusedLemmasBySite(pageEntity.getId());
            lemmasToDecrement.forEach(lemma -> {
                lemma.setFrequency(lemma.getFrequency() - 1);
            });
            lemmaRepository.saveAll(lemmasToDecrement);
            pageRepository.delete(pageEntity);
        } else {
            log.warn("Страница с URL: {} не найдена", url);
        }
    }

    @Transactional
    public void truncateAllSiteAndPages() {
        indexRepository.truncateAllIndexes();
        lemmaRepository.truncateAllLemmas();
        pageRepository.truncateAllPages();
        siteRepository.truncateAllSites();
    }

    public Float getAbsoluteRelevance(PageEntity page, List<String> lemmas){
        return indexRepository.calculatePageRelevance(page, lemmas);
    }

    public List<PageEntity> getPagesForSearchService(String lemma){
        return pageRepository.findPagesByLemma(lemma);
    }

    public SiteEntity getSiteForSearchService(String url){
        return siteRepository.findByUrl(url);
    }
}
