package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.LemmaEntity;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.utility.ConnectionUtil;
import searchengine.utility.LemmaExecute;
import searchengine.utility.PropertiesProject;
import searchengine.utility.UtilCheck;

import java.rmi.ConnectException;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiteTaskService {

    private final PropertiesProject property;
    private final StatusManager statusManager;
    private final RepositoryManager repositoryManager;

    public Document loadPageDocument(String url, SiteEntity siteEntity) {
        try {
            Connection connection = ConnectionUtil.getConnection(url, property.getReferrer(), property.getUserAgent());
            log.info("Возвращаем документ для {}", url);
            return connection.get();
        } catch (ConnectException ex) {
            log.warn("Ошибка подключения");
            return null;
        } catch (Exception e) {
            handleTaskError(url, e, siteEntity);
            return null;
        }
    }

    public boolean isValidUrl(String url, SiteEntity siteEntity) {
        String siteName = siteEntity.getUrl();
        log.info("Результат обработки isValidUrl: {}", !UtilCheck.isFileUrl(url) && UtilCheck.containsSiteName(url, siteName));
        return !UtilCheck.isFileUrl(url) && UtilCheck.containsSiteName(url, siteName);
    }

    private void handleTaskError(String url, Exception e, SiteEntity siteEntity) {
        log.error("Failed processing URL: {}", url, e);
        int code = ConnectionUtil.getStatusCode(url);
        statusManager.updateStatusPage(siteEntity, url, code != 0 ? code : 500);
        statusManager.updateStatusSiteFailed(siteEntity, e.getMessage());
    }

    public synchronized void processLemmas(Document document, SiteEntity siteEntity, PageEntity pageEntity) {
        log.info("Обрабатываем леммы для {}", pageEntity.getPath());
        try {
            String text = document.body().text();
            Map<String, Integer> lemmaMap = LemmaExecute.getLemmaMap(text);
            log.info("Отработал класс извлечения лемм LemmaExecute.getLemmaMap {}", lemmaMap.size());

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
