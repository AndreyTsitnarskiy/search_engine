package searchengine.utility;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.dto.PageInfo;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionUtils {
    private final ProjectParameters projectParameters;

    private Connection.Response getResponse(String url) throws IOException {
        return Jsoup.connect(url).maxBodySize(0)
                .userAgent(projectParameters.getUserAgent())
                .referrer(projectParameters.getReferrer())
                .header("Accept-Language", "ru")
                .ignoreHttpErrors(true)
                .execute();
    }

    public Set<String> getPaths(String content) {
        Document document = Jsoup.parse(content);
        return document.select("a[href]")
                .stream()
                .map(element -> element.attr("href"))
                .filter(path -> path.startsWith("/"))
                .collect(Collectors.toSet());
    }

    public PageInfo getPageInfo(String url) throws IOException, InterruptedException {
        Connection.Response response = getResponse(url);
        return new PageInfo(response.parse().html(), response.statusCode());
    }

    public String htmlToText(String content) {
        return Jsoup.parse(content).text();
    }

}
