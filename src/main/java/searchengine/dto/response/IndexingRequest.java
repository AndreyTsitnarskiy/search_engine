package searchengine.dto.response;

import org.antlr.v4.runtime.misc.NotNull;

public record IndexingRequest(@NotNull String url) {
}
