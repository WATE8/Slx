package searchengine.controllers;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api")
public class DefaultController {

    private final AtomicBoolean isIndexingInProgress = new AtomicBoolean(false);

    private final List<String> sites = List.of(
            "https://example.com",
            "https://example.org",
            "https://example.net"
    );

    private final HttpClient client = HttpClient.newHttpClient();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @RequestMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Map<String, Object>> startIndexing() {
        if (isIndexingInProgress.get()) {
            return ResponseEntity.badRequest().body(createErrorResponse("Индексация уже запущена"));
        }

        isIndexingInProgress.set(true);
        try {
            performFullIndexing();
            return ResponseEntity.ok(createSuccessResponse());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(createErrorResponse("Ошибка при запуске индексации: " + e.getMessage()));
        } finally {
            isIndexingInProgress.set(false);
        }
    }

    private void performFullIndexing() {
        System.out.println("Запуск индексации сайтов...");

        for (String site : sites) {
            try {
                System.out.println("Индексация сайта: " + site);

                String content = fetchSiteContent(site);
                processAndIndexContent(site, content);

                System.out.println("Индексация сайта завершена: " + site);
            } catch (Exception e) {
                System.err.println("Ошибка при индексации сайта: " + site + " - " + e.getMessage());
            }
        }

        System.out.println("Индексация всех сайтов завершена.");
    }

    private String fetchSiteContent(String siteUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(siteUrl))
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("Запрос был прерван при загрузке сайта: " + siteUrl);
        }

        if (response.statusCode() == 200) {
            return response.body();  // Возвращаем содержимое страницы
        } else {
            throw new IOException("Ошибка при загрузке сайта: " + siteUrl + ", код ответа: " + response.statusCode());
        }
    }

    private void processAndIndexContent(String siteUrl, String content) {
        try {
            Document doc = Jsoup.parse(content);
            String title = doc.title(); // Извлекаем заголовок страницы
            Elements paragraphs = doc.select("p"); // Извлекаем все абзацы страницы

            System.out.println("Заголовок: " + title);
            System.out.println("Текст страницы:");
            paragraphs.forEach(paragraph -> System.out.println(paragraph.text()));

            savePageToDatabase(siteUrl, title, content);

        } catch (Exception e) {
            System.err.println("Ошибка при обработке контента сайта: " + siteUrl + " - " + e.getMessage());
        }
    }

    private void savePageToDatabase(String siteUrl, String title, String content) {
        String sql = "INSERT INTO pages (site_url, title, content) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, siteUrl, title, content); // Используем JdbcTemplate для сохранения
    }

    private Map<String, Object> createSuccessResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("result", true);
        return response;
    }

    private Map<String, Object> createErrorResponse(String errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put("result", false);
        response.put("error", errorMessage);
        return response;
    }
}
