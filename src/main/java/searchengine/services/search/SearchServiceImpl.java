package searchengine.services.search;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.search.ApiSearchResponse;
import searchengine.dto.search.ApiSearchResult;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.services.managers.RepositoryManager;
import searchengine.utility.LemmaExecute;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final RepositoryManager repositoryManager;
    private static final int LEMMA_FREQUENCY_THRESHOLD_PERCENT = 10;
    private static final int MAX_RESULTS = 500;

    @Override
    public ResponseEntity<ApiSearchResponse> search(String query, String url, int offset, int limit) {
        log.info("Search query: {}, site: {}, offset: {}, limit: {}", query, url, offset, limit);

        if (query == null || query.trim().isEmpty()) {
            return successResponse(0, Collections.emptyList());
        }

        List<String> lemmas = LemmaExecute.getLemmaList(query);
        List<String> filteredLemmas = filterRareLemmas(lemmas);

        List<PageEntity> pages = getRelevantPages(filteredLemmas);
        if (pages.isEmpty()) {
            return successResponse(0, Collections.emptyList());
        }

        List<ApiSearchResult> results = calculateRelevance(pages, filteredLemmas);
        int total = results.size();
        results = results.stream().skip(offset).limit(limit).collect(Collectors.toList());

        return successResponse(total, results);
    }

    private List<String> filterRareLemmas(List<String> lemmas) {
        int totalPages = repositoryManager.getTotalPageCount();
        int threshold = (int) (totalPages * (LEMMA_FREQUENCY_THRESHOLD_PERCENT / 100.0));

        return lemmas.stream()
                .filter(lemma -> repositoryManager.getLemmaFrequency(lemma) < threshold)
                .collect(Collectors.toList());
    }

    private List<PageEntity> getRelevantPages(List<String> lemmas) {
        return repositoryManager.findTopPages(lemmas, lemmas.size(), MAX_RESULTS);
    }

    private List<ApiSearchResult> calculateRelevance(List<PageEntity> pages, List<String> lemmas) {
        Map<PageEntity, Float> relevanceMap = new HashMap<>();
        float maxRelevance = 0;

        for (PageEntity page : pages) {
            float relevance = repositoryManager.getAbsoluteRelevance(page, lemmas);
            log.info("Релевантность страницы {}: {}", page.getPath(), relevance);
            relevanceMap.put(page, relevance);
            maxRelevance = Math.max(maxRelevance, relevance);
        }

        List<ApiSearchResult> results = new ArrayList<>();
        for (PageEntity page : pages) {
            ApiSearchResult result = new ApiSearchResult();
            result.setSite(page.getSite().getUrl());
            result.setSiteName(page.getSite().getName());
            result.setUrl(page.getPath());
            result.setTitle(extractTitle(page.getContent()));
            result.setSnippet(generateSnippet(page.getContent(), lemmas));
            result.setRelevance(relevanceMap.get(page) / maxRelevance);

            results.add(result);
        }

        results.sort(Comparator.comparing(ApiSearchResult::getRelevance).reversed());
        return results;
    }

    private String generateSnippet(String content, List<String> lemmas) {
        int snippetSize = 200;
        int index = -1;
        for (String lemma : lemmas) {
            index = content.toLowerCase().indexOf(lemma);
            if (index != -1) break;
        }

        if (index == -1) return "";

        int start = Math.max(0, index - snippetSize / 2);
        int end = Math.min(content.length(), start + snippetSize);
        String snippet = content.substring(start, end);

        for (String lemma : lemmas) {
            snippet = snippet.replaceAll("(?i)" + lemma, "<b>" + lemma + "</b>");
        }

        return snippet;
    }

    private String extractTitle(String content) {
        Matcher matcher = Pattern.compile("<title>(.*?)</title>").matcher(content);
        return matcher.find() ? matcher.group(1) : "Без заголовка";
    }

    private ResponseEntity<ApiSearchResponse> successResponse(int count, List<ApiSearchResult> data) {
        ApiSearchResponse response = new ApiSearchResponse();
        response.setResult(true);
        response.setCount(count);
        response.setData(data);
        return ResponseEntity.ok(response);
    }
}
