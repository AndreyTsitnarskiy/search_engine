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

    private static final int MAX_RETRIES = 3;
    private static final int TIMEOUT = 5000; //

    public static Connection getConnection(String pagePath, String userAgent, String referrer) {
        return Jsoup.connect(pagePath)
                .userAgent(userAgent)
                .referrer(referrer)
                //.timeout(3000)
                .followRedirects(true)
                .ignoreHttpErrors(true);
    }

/*    public static Document getConnectionWithRetry(String url, String userAgent, String referrer, int retries) throws Exception {
        int attempts = 0;
        while (attempts < retries) {
            try {
                return ConnectionUtil.getConnection(url, referrer, userAgent).get();
            } catch (IOException e) {
                attempts++;
                if (attempts >= retries) {
                    throw new Exception("Failed to connect after " + retries + " attempts", e);
                }
                Thread.sleep(TIMEOUT);
            }
        }
        return null;
    }*/

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
