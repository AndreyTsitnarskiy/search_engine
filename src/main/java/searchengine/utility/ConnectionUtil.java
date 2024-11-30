package searchengine.utility;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.exceptions.SiteExceptions;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

@Log4j2
@UtilityClass
public class ConnectionUtil {

    public static Document getDocument(String url, String referrer, String userAgent){
        Document document = new Document(url);
        try {
            document = Jsoup.connect(url).userAgent(userAgent).referrer(referrer).timeout(2000).get();
        } catch (Exception e) {
            log.info("ERROR CONNECTION UTILS method getDocument");
            e.printStackTrace();
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
