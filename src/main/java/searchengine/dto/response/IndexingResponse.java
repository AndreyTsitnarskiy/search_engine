package searchengine.dto.response;

public record IndexingResponse(boolean result) {
    public IndexingResponse() {
        this(true);
    }
}
