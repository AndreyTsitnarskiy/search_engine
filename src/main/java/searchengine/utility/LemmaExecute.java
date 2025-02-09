package searchengine.utility;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.*;

@Slf4j
@UtilityClass
public class LemmaExecute {

    private static LuceneMorphology morphology;
    private static final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};

    private static void initMorphology() {
        if (morphology == null) {
            try {
                morphology = new RussianLuceneMorphology();
            } catch (IOException e) {
                log.error("Error initializing LuceneMorphology", e);
                throw new RuntimeException("Failed to initialize LuceneMorphology", e);
            }
        }
    }

    public static HashMap<String, Integer> getLemmaMap(String html) {
        initMorphology();
        HashMap<String, Integer> lemmaMap = new HashMap<>();
        String[] words = arrayRussianWords(html);

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            List<String> wordInfo = morphology.getMorphInfo(word);
            if (anyWordBaseBelongToParticle(wordInfo)) {
                continue;
            }
            List<String> normalizedWords = morphology.getNormalForms(word);
            if (normalizedWords.isEmpty()) {
                continue;
            }
            String normalizedWord = normalizedWords.get(0);
            lemmaMap.merge(normalizedWord, 1, Integer::sum);
        }
        return lemmaMap;
    }

    public static List<String> getLemmaList(String text) {
        initMorphology();
        String[] words = arrayRussianWords(text);
        log.info("Слова из запроса: {}", Arrays.toString(words));
        List<String> lemmaSet = new ArrayList<>();

        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            List<String> wordForms = morphology.getNormalForms(word);
            log.info("Нормальные формы для слова {}: {}", word, wordForms);
            if (anyWordBaseBelongToParticle(wordForms)) {
                lemmaSet.add(word);
                continue;
            }
            lemmaSet.addAll(wordForms);
        }
        log.info("Леммы из запроса: {}", lemmaSet);
        return lemmaSet;
    }

    private static String[] arrayRussianWords(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^а-я\\s]", "").trim().split("\\s+");
    }

    private static boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        return wordBaseForms.stream().anyMatch(form -> Arrays.stream(particlesNames).anyMatch(form::contains));
    }
}

