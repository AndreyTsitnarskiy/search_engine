package searchengine.utility;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class PropertiesProject {

    @Value("${connect.user-agent}")
    private String userAgent;

    @Value("${connect.referrer}")
    private String referrer;

}
