package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.entity.Status;
import searchengine.exceptions.SiteExceptions;
import searchengine.utility.ConnectionUtil;
import searchengine.utility.ReworkString;

import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;

@Slf4j
@RequiredArgsConstructor
public class CrawlerTask extends RecursiveAction {

    private final IndexingSitesImpl indexingSites;
    private final SiteEntity siteEntity;
    private final String pagePath;

    @SneakyThrows
    @Override
    protected void compute() {
        try {
            Thread.sleep(500);
            handlePageData();
        } catch (UnsupportedMimeTypeException | ConnectException | SiteExceptions ignoredException) {
            log.warn("WARNING " + ignoredException + " IN CONNECTION WHILE HANDLING " + pagePath);
        } catch (Exception exception) {
            log.warn("WARNING " + exception + " IN CONNECTION WHILE HANDLING " + pagePath +
                    " INDEXING FOR SITE " + siteEntity.getUrl() + " COMPLETED TO FAIL");
            indexingSites.getSiteStatusMap().put(siteEntity.getUrl(), Status.FAILED);
            throw exception;
        }
    }

    private void handlePageData() throws IOException {
        log.info("HANDING PAGE DATA: " + pagePath);
        List<CrawlerTask> pagesList = new ArrayList<>();
        String userAgent = indexingSites.getPropertiesProject().getUserAgent();
        String referrer = indexingSites.getPropertiesProject().getReferrer();
        Connection connection = ConnectionUtil.getConnection(pagePath, userAgent, referrer);
        int httpStatusCode = connection.execute().statusCode();
        if (httpStatusCode != 200) {
            connection = ConnectionUtil.getConnection(ReworkString.cutSlash(pagePath), userAgent, referrer);
            httpStatusCode = connection.execute().statusCode();
        }

        String pathToSave = ReworkString.cutProtocolAndHost(pagePath, siteEntity.getUrl());
        String html = "";
        PageEntity pageEntity = new PageEntity(siteEntity, pathToSave, httpStatusCode, html);
        if (httpStatusCode != 200) {
            indexingSites.savePageAndSiteStatusTime(pageEntity, html, siteEntity);
        } else {
            Document document = connection.get();
            html = document.outerHtml();
            indexingSites.savePageAndSiteStatusTime(pageEntity, html, siteEntity);
            log.info("Page indexed: " + pathToSave);
            indexingSites.extractLemmas(html, pageEntity, siteEntity);
            Elements anchors = document.select("body").select("a");
            handleAnchors(anchors, pagesList);
        }
        for (CrawlerTask siteParser : pagesList) {
            siteParser.join();
        }
    }

    private void handleAnchors(Elements elements, List<CrawlerTask> parserList) {
        String fileExtensions = indexingSites.getPropertiesProject().getFileExtensions();
        for (Element anchor : elements) {
            String href = ReworkString.getHrefFromAnchor(anchor);
            if (ReworkString.isHrefValid(siteEntity.getUrl(), href, fileExtensions)
                    && !ReworkString.isPageAdded(indexingSites.getWebPages(), href)) {
                indexingSites.getWebPages().add(href);
                if (!indexingSites.getSiteStatusMap().get(siteEntity.getUrl()).equals(Status.INDEXING)) {
                    return;
                }
                CrawlerTask siteParser = new CrawlerTask(indexingSites, siteEntity, href);
                parserList.add(siteParser);
                siteParser.fork();
            }
        }
    }
}
