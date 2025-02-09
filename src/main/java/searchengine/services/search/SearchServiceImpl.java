package searchengine.services.search;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.search.ApiSearchResponse;
import searchengine.dto.search.ApiSearchResult;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;
import searchengine.services.indexing.managers.RepositoryManager;
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

        // Валидация запроса
        if (query == null || query.trim().isEmpty()) {
            return errorResponse("Задан пустой поисковый запрос");
        }

        // Проверяем существование сайта, если передан URL
        SiteEntity site = null;
        if (!url.isEmpty()) {
            site = repositoryManager.getSiteForSearchService(url);
            if (site == null) {
                return errorResponse("Указанный сайт не найден в индексе");
            }
        }

        // Получаем список лемм
        List<String> lemmas = LemmaExecute.getLemmaList(query);
        if (lemmas.isEmpty()) {
            return errorResponse("Поисковый запрос не содержит значимых слов");
        }

        // Исключаем слишком частые леммы
        //lemmas = lemmaService.filterRareLemmas(lemmas);

        // Получаем список страниц, содержащих все леммы
        List<PageEntity> pages = getRelevantPages(lemmas, site);
        if (pages.isEmpty()) {
            return successResponse(0, Collections.emptyList());
        }

        // Рассчитываем релевантность
        List<ApiSearchResult> results = calculateRelevance(pages, lemmas);

        // Пагинация
        int total = results.size();
        results = results.stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());

        return successResponse(total, results);
    }

    private List<PageEntity> getRelevantPages(List<String> lemmas, SiteEntity site) {
        // Берем самую редкую лемму
        String rarestLemma = lemmas.get(0);
        List<PageEntity> pages = repositoryManager.getPagesForSearchService(rarestLemma, site);

        // Фильтруем по остальным леммам
        for (int i = 1; i < lemmas.size(); i++) {
            String lemma = lemmas.get(i);
            pages.retainAll(repositoryManager.getPagesForSearchService(lemma, site));
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
            relevanceMap.put(page, relevance);
            maxRelevance = Math.max(maxRelevance, relevance);
        }

        // Формируем результаты поиска
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
