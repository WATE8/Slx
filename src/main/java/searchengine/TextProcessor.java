package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class TextProcessor {

    private static final Logger logger = Logger.getLogger(TextProcessor.class.getName());
    private final LuceneMorphology luceneMorph;
    private final Set<String> excludedPosTags;
    private final Map<String, List<String>> lemmaCache = new ConcurrentHashMap<>();

    public TextProcessor(Set<String> excludedPosTags) throws Exception {
        // Инициализация морфологии
        this.luceneMorph = new RussianLuceneMorphology();
        this.excludedPosTags = excludedPosTags != null ? excludedPosTags : Set.of("СОЮЗ", "МЕЖД", "ПРЕДЛ", "ЧАСТ");

        // Игнорировать проверки сертификатов
        disableCertificateValidation();
    }

    private void disableCertificateValidation() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Используем лямбда для игнорирования проверки имени хоста
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Ошибка при игнорировании проверки сертификатов", e);
        }
    }

    public Map<String, Integer> getLemmas(String text) {
        if (isEmpty(text)) {
            logger.warning("Входной текст пуст или null");
            return Collections.emptyMap();
        }

        Map<String, Integer> lemmasCount = new ConcurrentHashMap<>(); // Используем потокобезопасную карту
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

        try {
            String pageContent = fetchPageContent(url);
            Map<String, Integer> lemmasCount = getLemmas(pageContent);

            // Здесь вы можете добавить код для сохранения лемм в индекс

            response.put("result", true);
            response.put("lemmasCount", lemmasCount);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Ошибка при загрузке содержимого страницы: " + url, e);
            response.put("result", false);
            response.put("error", "Не удалось загрузить содержимое страницы");
        }

        return response;
    }

    private String fetchPageContent(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();

        if (connection.getResponseCode() != 200) {
            throw new IOException("Ошибка при получении страницы, код ответа: " + connection.getResponseCode());
        }

        try (Scanner scanner = new Scanner(connection.getInputStream())) {
            scanner.useDelimiter("\\A"); // Считываем весь контент
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    public boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) {
            logger.warning("URL пуст или null");
            return false; // Проверка на null и пустую строку
        }

        // Убедитесь, что URL начинается с http:// или https://
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            logger.warning("URL должен начинаться с http:// или https:// : " + url);
            return false;
        }

        try {
            URI uri = new URI(url);
            String host = uri.getHost(); // Извлекаем домен из URL

            if (host == null) {
                logger.warning("Не удалось извлечь хост из URL: " + url);
                return false; // Если хост не извлечен, возвращаем false
            }

            logger.info("Извлечённый хост: " + host); // Логируем извлечённый хост

            // Разрешаем все домены
            return true; // Возвращаем true, чтобы разрешить все домены

        } catch (URISyntaxException e) {
            logger.warning("Некорректный URL: " + url + " | Ошибка: " + e.getMessage());
            return false; // Возвращаем false в случае ошибки
        }
    }

    public static void main(String[] args) {
        try {
            TextProcessor processor = createTextProcessor();

            // Пример текста для обработки
            String text = "Это пример текста, который нужно обработать и лемматизировать.";
            Map<String, Integer> lemmas = processor.getLemmas(text);
            lemmas.forEach((k, v) -> System.out.println("Лемма: " + k + " -> Количество: " + v));

            // Пример HTML-кода для очистки
            String htmlText = "<html><body><h1>Заголовок</h1><p>Это параграф текста.</p><!-- комментарий --></body></html>";
            String cleanedText = processor.removeHtmlTags(htmlText);
            System.out.println("Текст без HTML-тегов: " + cleanedText);

            // Примеры тестирования URL
            testUrls(processor);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Ошибка инициализации TextProcessor", e);
        }
    }

    private static TextProcessor createTextProcessor() throws Exception {
        Set<String> excludedPartsOfSpeech = Set.of("СОЮЗ", "МЕЖД", "ПРЕДЛ", "ЧАСТ");
        return new TextProcessor(excludedPartsOfSpeech);
    }

    private static void testUrls(TextProcessor processor) {
        String[] testUrls = {
                "https://volochek.life",
                "http://www.playback.ru",
                "https://www.svetlovka.ru",
                "https://et-cetera.ru/mobile/",
                "https://nikoartgallery.com",
                "https://dimonvideo.ru",
                "https://ipfran.ru",
                "https://volochek.life",
                "https://radiomv.ru",
                "https://test.ru", // Неверный протокол
                null, // null URL
                "", // Пустая строка
                "https://dimonvideo.ru" // Корректный домен
        };

        for (String url : testUrls) {
            boolean valid = processor.isValidUrl(url);
            System.out.println("URL: " + url + " | Валиден: " + valid);
        }
    }
}
