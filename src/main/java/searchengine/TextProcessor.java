package searchengine;

import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class TextProcessor {

    private static final Logger logger = Logger.getLogger(TextProcessor.class.getName());
    private final LemmaExtractor lemmaExtractor;

    // URL для подключения к базе данных
    private static final String DB_URL = "jdbc:mysql://localhost:3306/search_engine";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "asuzncmi666";

    public TextProcessor(Set<String> excludedPosTags) throws Exception {
        this.lemmaExtractor = new LemmaExtractor(excludedPosTags);
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
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Ошибка при игнорировании проверки сертификатов", e);
        }
    }

    public String removeHtmlTags(String htmlText) {
        if (isEmpty(htmlText)) {
            logger.warning("HTML-код пуст или null");
            return "";
        }
        return cleanHtml(htmlText);
    }

    private String cleanHtml(String htmlText) {
        String noComments = htmlText.replaceAll("<!--.*?-->", "");
        return noComments.replaceAll("<[^>]*>", "").trim();
    }

    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    public Map<String, Object> indexPage(String url, int siteId) {
        Map<String, Object> response = new HashMap<>();
        if (!isValidUrl(url)) {
            response.put("result", false);
            response.put("error", "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
            return response;
        }

        try {
            String pageContent = fetchPageContent(url);
            String cleanedContent = removeHtmlTags(pageContent); // Очистка HTML-тегов
            Map<String, Integer> lemmasCount = lemmaExtractor.getLemmas(cleanedContent); // Лемматизация очищенного текста

            // Сохранение лемм в базу данных
            saveLemmasToDatabase(lemmasCount, siteId);

            response.put("result", true);
            response.put("lemmasCount", lemmasCount);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Ошибка при загрузке содержимого страницы: " + url, e);
            response.put("result", false);
            response.put("error", "Не удалось загрузить содержимое страницы");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Ошибка при сохранении лемм в базу данных для URL: " + url, e);
            response.put("result", false);
            response.put("error", "Ошибка при сохранении лемм в базу данных");
        }

        return response;
    }

    private void saveLemmasToDatabase(Map<String, Integer> lemmasCount, int siteId) throws SQLException {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String insertQuery = "INSERT INTO lemmas (lemma, lemma_count, site_id) VALUES (?, ?, ?)";

            try (PreparedStatement preparedStatement = connection.prepareStatement(insertQuery)) {
                for (Map.Entry<String, Integer> entry : lemmasCount.entrySet()) {
                    preparedStatement.setString(1, entry.getKey());
                    preparedStatement.setInt(2, entry.getValue());
                    preparedStatement.setInt(3, siteId); // Указываем site_id
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch(); // Выполнение пакетной вставки
            }
        } catch (SQLException e) { // Обработка SQLException
            logger.log(Level.SEVERE, "Ошибка при сохранении лемм в базу данных", e);
            throw e; // Перебрасываем исключение для дальнейшей обработки, если необходимо
        }
    }

    private String fetchPageContent(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(false); // Отключаем автоматические перенаправления
        connection.connect();

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            // Если ответ 200 OK, считываем содержимое
            try (Scanner scanner = new Scanner(connection.getInputStream())) {
                scanner.useDelimiter("\\A");
                return scanner.hasNext() ? scanner.next() : "";
            }
        } else if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
            // Если ответ 301 или 302, извлекаем новый URL из заголовка Location
            String newUrl = connection.getHeaderField("Location");
            return fetchPageContent(newUrl); // Рекурсивно вызываем метод с новым URL
        } else {
            throw new IOException("Ошибка при получении страницы, код ответа: " + responseCode);
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
            TextProcessor processor = new TextProcessor(Set.of("СОЮЗ", "МЕЖД", "ПРЕДЛ", "ЧАСТ"));

            // Пример siteId для индексации
            int siteId = 1; // Убедитесь, что этот ID соответствует вашему сайту в базе данных

            // Добавление новых URL для индексации
            String[] testUrls = {
                    "https://volochek.life",
                    "http://radiomv.ru",
                    "http://www.playback.ru",
                    "https://ipfran.ru",
                    "https://dimonvideo.ru",
                    "https://nikoartgallery.com",
                    "https://www.svetlovka.ru"
            };

            for (String url : testUrls) {
                Map<String, Object> result = processor.indexPage(url, siteId);
                System.out.println("Результат индексации для " + url + ": " + result);
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Ошибка инициализации TextProcessor", e);
        }
    }
}