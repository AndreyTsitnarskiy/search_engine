package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.PageEntity;
import searchengine.utility.LemmaExecute;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveAction;

@Slf4j
@RequiredArgsConstructor
public class LemmaTask extends RecursiveAction {
    private static final int THRESHOLD = 20;

    private final List<PageEntity> pages;
    private final ConcurrentHashMap<PageEntity, Map<String, Integer>> global;

    @Override
    protected void compute() {
        if (pages.size() <= THRESHOLD) {
            processPages(pages);
        } else {
            int mid = pages.size() / 2;
            List<PageEntity> firstHalf = pages.subList(0, mid);
            List<PageEntity> secondHalf = pages.subList(mid, pages.size());

            invokeAll(
                    new LemmaTask(firstHalf, global),
                    new LemmaTask(secondHalf, global)
            );
        }
    }

    @Transactional
    private void processPages(List<PageEntity> pages) {
        for (PageEntity page : pages) {
            Map<String, Integer> lemmasInOnePage = extractionLemmaCount(page);
            global.put(page, lemmasInOnePage);
        }
    }

    private Map<String, Integer> extractionLemmaCount(PageEntity pageEntity) {
        Document document = Jsoup.parse(pageEntity.getContent());
        String text = document.body().text();
        document = null;
        return LemmaExecute.getLemmaMap(text);
    }
}
