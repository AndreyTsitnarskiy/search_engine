package searchengine.utility;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

@Slf4j
@UtilityClass
public class ConnectionUtils {

    public static Document getDocument(String url, String referrer, String userAgent) {
        Document document = null;
        try {
            document = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .referrer(referrer)
                    .timeout(2000)
                    .get();
        } catch (HttpStatusException e) {
            log.error("HTTP error fetching URL: Status=" + e.getStatusCode() + ", URL=" + e.getUrl());
        } catch (IOException e) {
            log.error("I/O error while fetching URL: " + url, e);
        } catch (Exception e) {
            log.error("Unknown error while fetching URL: " + url, e);
        }
        return document;
    }

    public static int getStatusCode(String url) {
        int code = 0;
        try {
            URL siteURL = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) siteURL.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            code = connection.getResponseCode();
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return code;
    }
}
