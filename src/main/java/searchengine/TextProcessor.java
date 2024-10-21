package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TextProcessor {

    private static final Logger logger = Logger.getLogger(TextProcessor.class.getName());
    private final LuceneMorphology luceneMorph;
    private final Set<String> excludedPosTags;
    private final Map<String, List<String>> lemmaCache = new ConcurrentHashMap<>();
    private final Set<String> allowedDomains;

    public TextProcessor(Set<String> excludedPosTags, Set<String> allowedDomains) throws Exception {
        this.luceneMorph = new RussianLuceneMorphology();
        this.excludedPosTags = excludedPosTags != null ? excludedPosTags : Set.of("СОЮЗ", "МЕЖД", "ПРЕДЛ", "ЧАСТ");
        this.allowedDomains = allowedDomains;
    }

    public Map<String, Integer> getLemmas(String text) {
        if (isEmpty(text)) {
            logger.warning("Входной текст пуст или null");
            return Collections.emptyMap();
        }

        Map<String, Integer> lemmasCount = new HashMap<>();
        String[] words = normalizeText(text).split("\\s+");

        Arrays.stream(words)
                .parallel()
                .filter(word -> !word.isEmpty())
                .forEach(word -> processWord(word, lemmasCount));

        return lemmasCount;
    }

    private void processWord(String word, Map<String, Integer> lemmasCount) {
        try {
            List<String> baseForms = getLemma(word);
            baseForms.forEach(baseForm -> lemmasCount.merge(baseForm, 1, Integer::sum));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Ошибка при обработке слова: " + word, e);
        }
    }

    private List<String> getLemma(String word) {
        return lemmaCache.computeIfAbsent(word, w -> {
            try {
                List<String> morphInfo = luceneMorph.getMorphInfo(w);
                if (!isExcluded(morphInfo)) {
                    return luceneMorph.getNormalForms(w);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Ошибка при получении морфологической информации для слова: " + w, e);
            }
            return Collections.emptyList();
        });
    }

    private boolean isExcluded(List<String> morphInfo) {
        return morphInfo.stream().anyMatch(info -> excludedPosTags.stream().anyMatch(info::contains));
    }

    public String removeHtmlTags(String htmlText) {
        if (isEmpty(htmlText)) {
            logger.warning("HTML-код пуст или null");
            return "";
        }
        return cleanHtml(htmlText);
    }

    private String normalizeText(String text) {
        return text.toLowerCase().replaceAll("[^а-яА-Я\\s]", " ").trim();
    }

    private String cleanHtml(String htmlText) {
        String noComments = htmlText.replaceAll("<!--.*?-->", "");
        return noComments.replaceAll("<[^>]*>", "").trim();
    }

    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    public Map<String, Object> indexPage(String url) {
        Map<String, Object> response = new HashMap<>();
        if (!isValidUrl(url)) {
            response.put("result", false);
            response.put("error", "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
            return response;
        }

        response.put("result", true);
        return response;
    }

    private boolean isValidUrl(String url) {
        return allowedDomains.stream().anyMatch(url::contains);
    }

    public static void main(String[] args) {
        try {
            Set<String> excludedPartsOfSpeech = Set.of("СОЮЗ", "МЕЖД", "ПРЕДЛ", "ЧАСТ");
            Set<String> allowedDomains = Set.of("example.com", "test.com");
            TextProcessor processor = new TextProcessor(excludedPartsOfSpeech, allowedDomains);

            String text = "Это пример текста, который нужно обработать и лемматизировать.";
            Map<String, Integer> lemmas = processor.getLemmas(text);
            lemmas.forEach((k, v) -> System.out.println("Лемма: " + k + " -> Количество: " + v));

            String htmlText = "<html><body><h1>Заголовок</h1><p>Это параграф текста.</p><!-- комментарий --></body></html>";
            String cleanedText = processor.removeHtmlTags(htmlText);
            System.out.println("Текст без HTML-тегов: " + cleanedText);

            String url = "http://example.com/page";
            Map<String, Object> indexResponse = processor.indexPage(url);
            System.out.println("Индексация страницы: " + indexResponse);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Ошибка инициализации TextProcessor", e);
        }
    }
}