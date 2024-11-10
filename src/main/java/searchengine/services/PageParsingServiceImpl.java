package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.entity.IndexEntity;
import searchengine.entity.LemmaEntity;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.services.interfaces.PageParsingService;
import searchengine.utility.LemmasExecute;
import searchengine.utility.ProjectParameters;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageParsingServiceImpl implements PageParsingService {

    private final PageRepository pageRepository;
    private final ProjectParameters projectParameters;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    //Оптимизация через локальные мьютексы public void saveLemmasBySite
    //private final ConcurrentHashMap<String, Object> lemmaLocks = new ConcurrentHashMap<>();

    @Override
    public void parsePage(String pageUrl, SiteEntity siteEntity){
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        try {
            log.info("Активные потоки: " + forkJoinPool.getActiveThreadCount());
            log.info("Запланированные задачи: " + forkJoinPool.getQueuedTaskCount());
            forkJoinPool.invoke(new PageTask(pageUrl, siteEntity, this, projectParameters));
            log.info("Все задачи завершены.");

        } finally {
            forkJoinPool.shutdown();
            log.info("ForkJoinPool завершён.");
        }
    }

    public void savePageEntity(PageEntity pageEntity){
        pageRepository.save(pageEntity);
    }

    // синхронизацией и атомарными операциями для сохранения
    public synchronized void saveLemmasBySite(Set<LemmaEntity> lemmaEntitySet) {
        for (LemmaEntity lemma : lemmaEntitySet) {
            LemmaEntity existingLemma = lemmaRepository.findByLemmaAndSiteEntity(lemma.getLemma(), lemma.getSiteEntity());
            if (existingLemma != null) {
                existingLemma.setFrequency(existingLemma.getFrequency() + lemma.getFrequency());
                lemmaRepository.save(existingLemma);
            } else {
                lemmaRepository.save(lemma);
            }
        }
    }

    //Оптимизация через локальные мьютексы
/*    public void saveLemmasBySite(Set<LemmaEntity> lemmaEntitySet) {
        for (LemmaEntity lemma : lemmaEntitySet) {
            Object lock = lemmaLocks.computeIfAbsent(lemma.getLemma(), k -> new Object());
            synchronized (lock) {
                LemmaEntity existingLemma = lemmaRepository.findByLemmaAndSiteEntity(lemma.getLemma(), lemma.getSiteEntity());
                if (existingLemma != null) {
                    existingLemma.setFrequency(existingLemma.getFrequency() + lemma.getFrequency());
                    lemmaRepository.save(existingLemma);
                } else {
                    lemmaRepository.save(lemma);
                }
            }
            lemmaLocks.remove(lemma.getLemma());
        }
    }*/

    public void saveAllIndexesByPage(Set<IndexEntity> indexEntitySet){
        indexRepository.saveAll(indexEntitySet);
    }

    public Set<IndexEntity> parsingAndCreateIndexes(Set<LemmaEntity> lemmaEntitySet, PageEntity pageEntity, HashMap<String, Integer> mapLemmas){
        Set<IndexEntity> indexEntitySet = new HashSet<>();
        for (LemmaEntity lemma : lemmaEntitySet) {
            IndexEntity indexEntity = new IndexEntity();
            indexEntity.setPageEntity(pageEntity);
            indexEntity.setLemmaEntity(lemma);
            indexEntity.setRank(mapLemmas.get(lemma.getLemma()));
            indexEntitySet.add(indexEntity);
        }
        return indexEntitySet;
    }

    public Set<LemmaEntity> parsingBodyOnLemmas(HashMap<String, Integer> lemmaWorldsMap, SiteEntity siteEntity) {
        Set<LemmaEntity> lemmaEntitySet = new HashSet<>();

        // Заранее получаем все существующие леммы для текущего сайта
        Map<String, LemmaEntity> existingLemmas = lemmaRepository.findAllBySiteEntity(siteEntity)
                .stream()
                .collect(Collectors.toMap(LemmaEntity::getLemma, lemma -> lemma));

        for (Map.Entry<String, Integer> lemmasAndCount : lemmaWorldsMap.entrySet()) {
            String lemma = lemmasAndCount.getKey();
            int count = lemmasAndCount.getValue();

            LemmaEntity lemmaEntity = existingLemmas.get(lemma);

            if (lemmaEntity == null) {
                // Лемма не найдена в базе - создаем новую
                lemmaEntity = new LemmaEntity();
                lemmaEntity.setSiteEntity(siteEntity);
                lemmaEntity.setLemma(lemma);
                lemmaEntity.setFrequency(count);
                existingLemmas.put(lemma, lemmaEntity); // Добавляем в карту для следующих проверок
            } else {
                // Лемма уже существует, увеличиваем частоту
                lemmaEntity.setFrequency(lemmaEntity.getFrequency() + count);
            }

            lemmaEntitySet.add(lemmaEntity);
        }

        return lemmaEntitySet;
    }

    public HashMap<String, Integer> parseTextLemma(String text){
        return LemmasExecute.getLemmaMap(text);
    }
}
