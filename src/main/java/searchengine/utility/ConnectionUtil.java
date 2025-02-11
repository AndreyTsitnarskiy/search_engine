package searchengine.utility;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

@Log4j2
@UtilityClass
public class ConnectionUtil {

    private static final int TIMEOUT = 30000;

    public static Connection getConnection(String pagePath, String userAgent, String referrer) {
        return Jsoup.connect(pagePath)
                .userAgent(userAgent)
                .referrer(referrer)
                .timeout(TIMEOUT)
                .followRedirects(true)
                .ignoreHttpErrors(true);
    }

    public static int getStatusCode(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            return connection.getResponseCode();
        } catch (IOException e) {
            log.error("Ошибка при получении статуса URL {}: {}", url, e.getMessage());
            return 500;
        }
    }
}
