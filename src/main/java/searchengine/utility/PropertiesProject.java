package searchengine.utility;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
public class PropertiesProject {

    @Value("${connect.user-agent}")
    private String userAgent;

    @Value("${connect.referrer}")
    private String referrer;

    @Value("${file.extensions}")
    private String fileExtensions;

    @Value("${error.interrupted}")
    private String interruptedByUserMessage;

    @Value("${error.certificate}")
    private String certificateError;

    @Value("${error.unknown}")
    private String unknownError;
}
