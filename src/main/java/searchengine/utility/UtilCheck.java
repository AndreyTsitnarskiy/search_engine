package searchengine.utility;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

@Slf4j
@UtilityClass
public class UtilCheck {

    private static final String WWW = "https://wwww";
    private static final String HTTPS = "https://";

    private static final Pattern FILE_EXTENSION_PATTERN = Pattern.compile(
            ".*\\.(pdf|docx?|xlsx?|jpg|jpeg|gif|png|mp3|mp4|aac|json|csv|exe|apk|rar|zip|xml|jar|bin|svg|nc|webp|m|fig|eps)$",
            Pattern.CASE_INSENSITIVE
    );

    public static boolean isFileUrl(String url) {
        return FILE_EXTENSION_PATTERN.matcher(url).matches();
    }

    public static String reworkUrl(String in){
        if(in.startsWith(WWW)){
            in.substring(WWW.length());
        } else if (in.startsWith(HTTPS)){
            in.substring(HTTPS.length());
        }
        return in;
    }

    public static boolean containsSiteName(String url, String siteName) {
        String cleanedUrl = reworkUrl(url);
        String cleanedSiteName = reworkUrl(siteName);
        return cleanedUrl.startsWith(cleanedSiteName);
    }
}
