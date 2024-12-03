package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.IndexEntity;
import searchengine.entity.LemmaEntity;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.services.indexing.interfaces.LemmaProcessService;
import searchengine.utility.LemmaExecute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LemmaProcessServiceImpl implements LemmaProcessService {

    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    @Override
    public void parsingAndSaveContent(SiteEntity siteEntity, List<PageEntity> listPages) {
        for (PageEntity page : listPages) {
            Map<String, Integer> lemmasInOnePage = extractionLemmaCount(page);
            processPageLemmas(siteEntity, page, lemmasInOnePage);
        }
    }

    @Transactional
    private void processPageLemmas(SiteEntity siteEntity, PageEntity pageEntity, Map<String, Integer> lemmasInOnePage) {
        List<LemmaEntity> existingLemmas = lemmaRepository
                .getExistsLemmas(new ArrayList<>(lemmasInOnePage.keySet()), siteEntity.getId());

        Map<String, LemmaEntity> existingLemmaMap = existingLemmas.stream()
                .collect(Collectors.toMap(LemmaEntity::getLemma, lemma -> lemma));

        List<LemmaEntity> oldLemmas = new ArrayList<>();
        List<LemmaEntity> newLemmas = new ArrayList<>();
        List<IndexEntity> indices = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : lemmasInOnePage.entrySet()) {
            String lemmaText = entry.getKey();
            int count = entry.getValue();

            LemmaEntity lemmaEntity = existingLemmaMap.get(lemmaText);
            if (lemmaEntity != null) {
                lemmaEntity.setFrequency(lemmaEntity.getFrequency() + 1);
                oldLemmas.add(lemmaEntity);
            } else {
                lemmaEntity = new LemmaEntity();
                lemmaEntity.setLemma(lemmaText);
                lemmaEntity.setFrequency(1);
                lemmaEntity.setSite(siteEntity);
                newLemmas.add(lemmaEntity);
            }

            indices.add(new IndexEntity(pageEntity, lemmaEntity, count));
        }
        lemmaRepository.saveAll(oldLemmas);
        if (!newLemmas.isEmpty()) {
            lemmaRepository.saveAll(newLemmas);
        }
        if (!indices.isEmpty()) {
            indexRepository.saveAll(indices);
        }
    }

    private HashMap<String, Integer> extractionLemmaCount(PageEntity pageEntity) {
        Document document = Jsoup.parse(pageEntity.getContent());
        String text = document.body().text();
        return LemmaExecute.getLemmaMap(text);
    }
}
