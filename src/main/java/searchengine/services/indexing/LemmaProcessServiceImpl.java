package searchengine.services.indexing;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.IndexEntity;
import searchengine.entity.LemmaEntity;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.services.indexing.interfaces.LemmaProcessService;

import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LemmaProcessServiceImpl implements LemmaProcessService {

    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void parsingAndSaveContent(SiteEntity siteEntity, List<PageEntity> listPages) {
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        HashMap<PageEntity, Map<String, Integer>> globalPages = new HashMap<>();

        forkJoinPool.invoke(new LemmaTask(listPages, globalPages));
        forkJoinPool.shutdown();
        processLemmasInIndexes(globalPages, siteEntity);
        globalPages.clear();
        entityManager.clear();
    }

    private void processLemmasInIndexes(HashMap<PageEntity, Map<String, Integer>> globalPages, SiteEntity siteEntity) {
        List<IndexEntity> indexEntities = new ArrayList<>();

        List<String> lemmaInBatchPages = globalPages.values().stream()
                .flatMap(lemmasMap -> lemmasMap.keySet().stream())
                .distinct()
                .toList();
        Map<String, LemmaEntity> lemmaMapFromDataBase = lemmaRepository.getExistsLemmas(lemmaInBatchPages, siteEntity.getId())
                .stream()
                .collect(Collectors.toMap(LemmaEntity::getLemma, lemma -> lemma));

        for (Map.Entry<PageEntity, Map<String, Integer>> entry : globalPages.entrySet()) {
            for (Map.Entry<String, Integer> lemmas : entry.getValue().entrySet()) {
                String lemmaKey = lemmas.getKey();

                LemmaEntity lemmaEntity;

                if(lemmaMapFromDataBase.containsKey(lemmaKey)){
                    lemmaEntity = lemmaMapFromDataBase.get(lemmaKey);
                    lemmaEntity.setFrequency(lemmaEntity.getFrequency() + 1);
                    lemmaMapFromDataBase.put(lemmaKey, lemmaEntity);
                } else {
                    lemmaEntity = new LemmaEntity();
                    lemmaEntity.setSite(siteEntity);
                    lemmaEntity.setFrequency(1);
                    lemmaEntity.setLemma(lemmaKey);
                    lemmaMapFromDataBase.put(lemmaKey, lemmaEntity);
                }
                IndexEntity index = new IndexEntity();
                index.setPage(entry.getKey());
                index.setRank(lemmas.getValue());
                index.setLemma(lemmaEntity);
                indexEntities.add(index);
            }
        }
        batchSaveAllLemmas(new ArrayList<>(lemmaMapFromDataBase.values()));
        lemmaMapFromDataBase.clear();
        batchSaveAllIndexes(indexEntities);
        indexEntities.clear();
    }

    @Override
    @Transactional
    public void deleteAllLemmasAndIndexes(){
        indexRepository.deleteAll();
        lemmaRepository.deleteAll();
    }

    @Transactional
    private void batchSaveAllLemmas(List<LemmaEntity> lemmaEntityInBatch) {
        int batchSize = 1000;
        for (int i = 0; i < lemmaEntityInBatch.size(); i += batchSize) {
            int end = Math.min(i + batchSize, lemmaEntityInBatch.size());
            List<LemmaEntity> tempBatch = lemmaEntityInBatch.subList(i, end);
            lemmaRepository.saveAll(tempBatch);
        }
    }

    @Transactional
    private void batchSaveAllIndexes(List<IndexEntity> indexEntitiesInBatch) {
        int batchSize = 1000;
        for (int i = 0; i < indexEntitiesInBatch.size(); i += batchSize) {
            int end = Math.min(i + batchSize, indexEntitiesInBatch.size());
            List<IndexEntity> tempBatch = indexEntitiesInBatch.subList(i, end);
            indexRepository.saveAll(tempBatch);
        }
    }
}
