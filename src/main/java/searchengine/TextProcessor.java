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

    // Список частей речи, которые нужно исключить
    private final Set<String> excludedPosTags;

    // Кеш для сохранения уже обработанных слов
    private final Map<String, List<String>> lemmaCache = new ConcurrentHashMap<>();

    public TextProcessor(Set<String> excludedPosTags) throws Exception {
        this.luceneMorph = new RussianLuceneMorphology();
        this.excludedPosTags = excludedPosTags != null ? excludedPosTags : Set.of("СОЮЗ", "МЕЖД", "ПРЕДЛ", "ЧАСТ");
    }

    /**
     * Метод для лемматизации текста с исключением служебных частей речи
     *
     * @param text Входной текст
     * @return HashMap<String, Integer> - ключи: леммы, значения: их количество
     */
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

    /**
     * Обрабатывает слово и добавляет леммы в счетчик
     *
     * @param word       Слово для обработки
     * @param lemmasCount Карта для подсчета лемм
     */
    private void processWord(String word, Map<String, Integer> lemmasCount) {
        try {
            List<String> baseForms = getLemma(word);
            baseForms.forEach(baseForm -> lemmasCount.merge(baseForm, 1, Integer::sum));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Ошибка при обработке слова: " + word, e);
        }
    }

    /**
     * Получает леммы слова с кешированием
     *
     * @param word Слово для обработки
     * @return Список лемм (базовых форм) слова
     */
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

    /**
     * Проверяет, нужно ли исключить слово по его частям речи
     *
     * @param morphInfo Список морфологической информации
     * @return true, если слово нужно исключить; false в противном случае
     */
    private boolean isExcluded(List<String> morphInfo) {
        return morphInfo.stream().anyMatch(info -> excludedPosTags.stream().anyMatch(info::contains));
    }

    /**
     * Метод для удаления HTML-тегов и комментариев из текста
     *
     * @param htmlText HTML-код
     * @return Чистый текст без HTML-тегов
     */
    public String removeHtmlTags(String htmlText) {
        if (isEmpty(htmlText)) {
            logger.warning("HTML-код пуст или null");
            return "";
        }

        return cleanHtml(htmlText);
    }

    /**
     * Нормализует текст: удаляет лишние символы и приводит к нижнему регистру
     *
     * @param text Входной текст
     * @return Нормализованный текст
     */
    private String normalizeText(String text) {
        return text.toLowerCase().replaceAll("[^а-яА-Я\\s]", " ").trim();
    }

    /**
     * Очищает HTML-код от тегов и комментариев
     *
     * @param htmlText HTML-код
     * @return Чистый текст
     */
    private String cleanHtml(String htmlText) {
        String noComments = htmlText.replaceAll("<!--.*?-->", "");
        return noComments.replaceAll("<[^>]*>", "").trim();
    }

    /**
     * Проверяет, является ли строка пустой или null
     *
     * @param str Строка для проверки
     * @return true, если строка пустая или null; false в противном случае
     */
    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static void main(String[] args) {
        try {
            Set<String> excludedPartsOfSpeech = Set.of("СОЮЗ", "МЕЖД", "ПРЕДЛ", "ЧАСТ");
            TextProcessor processor = new TextProcessor(excludedPartsOfSpeech);

            // Пример текста
            String text = "Это пример текста, который нужно обработать и лемматизировать.";

            // Лемматизация текста
            Map<String, Integer> lemmas = processor.getLemmas(text);
            lemmas.forEach((k, v) -> System.out.println("Лемма: " + k + " -> Количество: " + v));

            // Пример HTML
            String htmlText = "<html><body><h1>Заголовок</h1><p>Это параграф текста.</p><!-- комментарий --></body></html>";

            // Очистка от HTML-тегов
            String cleanedText = processor.removeHtmlTags(htmlText);
            System.out.println("Текст без HTML-тегов: " + cleanedText);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Ошибка инициализации TextProcessor", e);
        }
    }
}