package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.entity.LemmaEntity;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.services.indexing.managers.RepositoryManager;
import searchengine.utility.ConnectionUtil;
import searchengine.utility.LemmaExecute;
import searchengine.utility.PropertiesProject;
import searchengine.utility.UtilCheck;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiteTaskService {

    private final PropertiesProject property;
    private final RepositoryManager repositoryManager;

    public Document loadPageDocument(String url) {
        try {
            Connection connection = ConnectionUtil.getConnection(url, property.getReferrer(), property.getUserAgent());
            return connection.get();
        } catch (Exception e) {
            log.error("Не удалось установить соединение с {}", url);
            return null;
        }
    }

    public boolean isValidUrl(String url, SiteEntity siteEntity) {
        String siteName = siteEntity.getUrl();
        return !UtilCheck.isFileUrl(url) && UtilCheck.containsSiteName(url, siteName);
    }


    public void processLemmas(Document document, SiteEntity siteEntity, PageEntity pageEntity) {
        try {
            String text = document.body().text();
            Map<String, Integer> lemmaMap = LemmaExecute.getLemmaMap(text);

            for (Map.Entry<String, Integer> lemmaEntry : lemmaMap.entrySet()) {
                String lemma = lemmaEntry.getKey();
                Integer frequency = lemmaEntry.getValue();

                LemmaEntity lemmaEntity = repositoryManager.processAndSaveLemma(siteEntity, lemma);
                repositoryManager.saveIndex(pageEntity, lemmaEntity, frequency);
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке лемм для страницы: {}", e.getMessage());
        }
    }
}
