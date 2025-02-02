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
