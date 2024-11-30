package searchengine.services.indexing;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.utility.PropertiesProject;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RecursiveTask;

@Slf4j
@AllArgsConstructor
public class SiteIndexingTask extends RecursiveTask<Void> {
    private final String url;
    private final SiteEntity siteEntity;
    private final Set<String> visitedUrls;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    @Override
    protected Void compute() {
        if (!visitedUrls.add(url)) return null;

        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent("HeliontSearchBot")
                    .referrer("https://google.com")
                    .execute();

            int statusCode = response.statusCode();
            Document doc = response.parse();

            // Сохранение страницы
            PageEntity page = new PageEntity(siteEntity, url, statusCode, doc.html());
            pageRepository.save(page);
            log.info("Save page: " + page.getPath());

            // Поиск всех ссылок
            List<SiteIndexingTask> subTasks = doc.select("a[href]").stream()
                    .map(link -> link.attr("abs:href"))
                    .filter(link -> link.startsWith("http"))
                    .map(link -> new SiteIndexingTask(link, siteEntity, visitedUrls, siteRepository, pageRepository))
                    .toList();

            // Запуск подзадач
            invokeAll(subTasks);
        } catch (IOException e) {
            siteRepository.updateStatus(siteEntity.getId(), "FAILED", e.getMessage());
        }
        return null;
    }
}
