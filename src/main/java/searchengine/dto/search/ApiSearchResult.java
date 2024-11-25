package searchengine.dto.search;

import lombok.Data;

@Data
public class ApiSearchResult {
    private String site;
    private String siteName;
    private String url;
    private String title;
    private String snippet;
    private float relevance;
}
