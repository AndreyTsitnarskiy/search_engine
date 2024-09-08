package searchengine.utility;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;

import java.net.HttpURLConnection;
import java.net.URL;

@Slf4j
@UtilityClass
public class ConnectionUtils {

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
