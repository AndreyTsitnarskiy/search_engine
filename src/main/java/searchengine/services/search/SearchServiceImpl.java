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

    @Override
    public ResponseEntity<ApiSearchResponse> search(String query, String url, int offset, int limit) {
        log.info("Search query: {}, site: {}, offset: {}, limit: {}", query, url, offset, limit);

        if (query == null || query.trim().isEmpty()) {
            log.warn("Пустой поисковый запрос");
            return errorResponse("Задан пустой поисковый запрос");
        }

        SiteEntity site = null;
        if (!url.isEmpty()) {
            site = repositoryManager.getSiteForSearchService(url);
            if (site == null) {
                log.warn("Сайт не найден: {}", url);
                return errorResponse("Указанный сайт не найден в индексе");
            }
        }

        List<String> lemmas = LemmaExecute.getLemmaList(query);
        if (lemmas.isEmpty()) {
            log.warn("Поисковый запрос не содержит значимых слов");
            return errorResponse("Поисковый запрос не содержит значимых слов");
        }

        // Исключаем слишком частые леммы
        //lemmas = lemmaService.filterRareLemmas(lemmas);

        List<PageEntity> pages = getRelevantPages(lemmas);
        if (pages.isEmpty()) {
            return successResponse(0, Collections.emptyList());
        }

        List<ApiSearchResult> results = calculateRelevance(pages, lemmas);
        log.info("Результаты поиска: {}", results.size());

        // Пагинация
        int total = results.size();
        results = results.stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());

        return successResponse(total, results);
    }

    private List<PageEntity> getRelevantPages(List<String> lemmas) {
        // Берем самую редкую лемму
        String rarestLemma = lemmas.get(0);
        log.info("Самая редкая лемма: {}", rarestLemma);
        List<PageEntity> pages = repositoryManager.getPagesForSearchService(rarestLemma);
        log.info("Страницы для самой редкой леммы: {}", pages.size());

        // Фильтруем по остальным леммам
        for (int i = 1; i < lemmas.size(); i++) {
            String lemma = lemmas.get(i);
            log.info("Фильтрация по лемме: {}", lemma);
            List<PageEntity> filteredPages = repositoryManager.getPagesForSearchService(lemma);
            log.info("Страницы для леммы {}: {}", lemma, filteredPages.size());
            pages.retainAll(filteredPages);
            log.info("Страницы после фильтрации: {}", pages.size());
            if (pages.isEmpty()) break;
        }
        return pages;
    }

    private List<ApiSearchResult> calculateRelevance(List<PageEntity> pages, List<String> lemmas) {
        // Вычисляем абсолютную релевантность
        Map<PageEntity, Float> relevanceMap = new HashMap<>();
        float maxRelevance = 0;

        for (PageEntity page : pages) {
            float relevance = repositoryManager.getAbsoluteRelevance(page, lemmas);
            log.info("Релевантность страницы {}: {}", page.getPath(), relevance);
            relevanceMap.put(page, relevance);
            maxRelevance = Math.max(maxRelevance, relevance);
        }

        // Формируем результаты поиска
        List<ApiSearchResult> results = new ArrayList<>();
        for (PageEntity page : pages) {
            ApiSearchResult result = new ApiSearchResult();
            result.setSite(page.getSite().getUrl() + page.getPath());
            result.setSiteName(page.getSite().getName());
            result.setUrl(page.getSite().getUrl() + page.getPath());
            result.setTitle(extractTitle(page.getContent()));
            result.setSnippet(generateSnippet(page.getContent(), lemmas));
            result.setRelevance(relevanceMap.get(page) / maxRelevance);

            results.add(result);
        }

        // Сортируем по убыванию релевантности
        results.sort(Comparator.comparing(ApiSearchResult::getRelevance).reversed());
        return results;
    }

    private String generateSnippet(String content, List<String> lemmas) {
        // Простейший алгоритм поиска фрагмента с совпадениями
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

        // Выделяем жирным найденные слова
        for (String lemma : lemmas) {
            snippet = snippet.replaceAll("(?i)" + lemma, "<b>" + lemma + "</b>");
        }

        return snippet;
    }

    private String extractTitle(String content) {
        Matcher matcher = Pattern.compile("<title>(.*?)</title>").matcher(content);
        return matcher.find() ? matcher.group(1) : "Без заголовка";
    }

    private ResponseEntity<ApiSearchResponse> errorResponse(String message) {
        ApiSearchResponse response = new ApiSearchResponse();
        response.setResult(false);
        response.setMessageError(message);
        return ResponseEntity.badRequest().body(response);
    }

    private ResponseEntity<ApiSearchResponse> successResponse(int count, List<ApiSearchResult> data) {
        ApiSearchResponse response = new ApiSearchResponse();
        response.setResult(true);
        response.setCount(count);
        response.setData(data);
        return ResponseEntity.ok(response);
    }
}
