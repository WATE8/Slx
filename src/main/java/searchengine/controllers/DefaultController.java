package searchengine.controllers;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api")
public class DefaultController {

    private final AtomicBoolean isIndexingInProgress = new AtomicBoolean(false);
    private final List<ForkJoinPool> activePools = Collections.synchronizedList(new ArrayList<>());
    private static final Logger logger = LoggerFactory.getLogger(DefaultController.class);

    private final List<String> sites = List.of(
            "https://example.com",
            "https://example.org",
            "https://example.net"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/startIndexing")
    public ResponseEntity<Map<String, Object>> startIndexing() {
        if (isIndexingInProgress.get()) {
            logger.warn("Индексация уже запущена");
            return ResponseEntity.badRequest().body(createErrorResponse("Индексация уже запущена"));
        }

        isIndexingInProgress.set(true);
        ForkJoinPool pool = new ForkJoinPool();
        activePools.add(pool);

        try {
            pool.submit(() -> {
                performFullIndexing();
                return null;
            }).get();

            return ResponseEntity.ok(createSuccessResponse());
        } catch (Exception e) {
            logger.error("Ошибка при запуске индексации", e);
            return ResponseEntity.status(500).body(createErrorResponse("Ошибка при запуске индексации: " + e.getMessage()));
        } finally {
            pool.shutdown();
            activePools.remove(pool);
            try {
                if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.error("Ошибка при завершении ForkJoinPool", e);
                pool.shutdownNow();
            }
            isIndexingInProgress.set(false);
        }
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Map<String, Object>> stopIndexing() {
        if (!isIndexingInProgress.get()) {
            logger.warn("Попытка остановить индексацию, которая не запущена");
            return ResponseEntity.badRequest().body(createErrorResponse("Индексация не запущена"));
        }

        isIndexingInProgress.set(false);

        for (ForkJoinPool pool : activePools) {
            pool.shutdownNow();
        }
        activePools.clear();

        for (String site : sites) {
            String currentStatus = getSiteStatus(site);
            if ("INDEXING".equals(currentStatus)) {
                updateSiteStatus(site, "FAILED", "Индексация остановлена пользователем");
            }
        }

        logger.info("Индексация успешно остановлена");
        return ResponseEntity.ok(createSuccessResponse());
    }

    private String getSiteStatus(String siteUrl) {
        String sql = "SELECT status FROM site WHERE url = ?";
        return jdbcTemplate.queryForObject(sql, String.class, siteUrl);
    }

    private void performFullIndexing() {
        for (String site : sites) {
            if (!isIndexingInProgress.get()) {
                logger.info("Индексация остановлена перед обработкой сайта: {}", site);
                break;
            }

            try {
                logger.info("Индексация сайта: {}", site);

                deleteExistingSiteData(site);

                updateSiteStatus(site, "INDEXING", null);

                ForkJoinPool pool = new ForkJoinPool();
                activePools.add(pool);
                try {
                    pool.invoke(new PageCrawler(site, site));
                } finally {
                    pool.shutdown();
                    activePools.remove(pool);
                }

                updateSiteStatus(site, "INDEXED", null);
                logger.info("Индексация сайта завершена: {}", site);
            } catch (Exception e) {
                updateSiteStatus(site, "FAILED", e.getMessage());
                logger.error("Ошибка при индексации сайта: {}", site, e);
            }
        }
    }

    private void deleteExistingSiteData(String siteUrl) {
        jdbcTemplate.update("DELETE FROM page WHERE site_url = ?", siteUrl);
        jdbcTemplate.update("DELETE FROM site WHERE url = ?", siteUrl);
    }

    private void updateSiteStatus(String siteUrl, String status, String error) {
        String sql = "UPDATE site SET status = ?, status_time = NOW(), last_error = ? WHERE url = ?";
        jdbcTemplate.update(sql, status, error, siteUrl);
    }

    private void savePageToDatabase(String siteUrl, String pageUrl, String content) {
        String sql = "INSERT INTO page (site_url, url, content) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, siteUrl, pageUrl, content);
    }

    private class PageCrawler extends RecursiveTask<Void> {
        private final String siteUrl;
        private final String pageUrl;

        public PageCrawler(String siteUrl, String pageUrl) {
            this.siteUrl = siteUrl;
            this.pageUrl = pageUrl;
        }

        @Override
        protected Void compute() {
            try {
                if (!isIndexingInProgress.get()) {
                    logger.info("Индексация остановлена пользователем для сайта: {}", siteUrl);
                    updateSiteStatus(siteUrl, "FAILED", "Индексация остановлена пользователем");
                    return null;
                }

                Document doc = Jsoup.connect(pageUrl)
                        .userAgent("CustomSearchBot")
                        .referrer("http://www.google.com")
                        .get();

                savePageToDatabase(siteUrl, pageUrl, doc.html());

                Thread.sleep(500 + new Random().nextInt(4500));

                Elements links = doc.select("a[href]");
                List<PageCrawler> tasks = new ArrayList<>();

                for (var link : links) {
                    String nextUrl = link.absUrl("href");
                    if (!isPageIndexed(nextUrl)) {
                        PageCrawler task = new PageCrawler(siteUrl, nextUrl);
                        tasks.add(task);
                        task.fork();
                    }
                }

                for (PageCrawler task : tasks) {
                    task.join();
                }

            } catch (IOException | InterruptedException e) {
                logger.error("Ошибка при индексации страницы: {}", pageUrl, e);
                updateSiteStatus(siteUrl, "FAILED", "Ошибка при обработке страницы: " + pageUrl);
                Thread.currentThread().interrupt();
            }
            return null;
        }

        private boolean isPageIndexed(String pageUrl) {
            String sql = "SELECT COUNT(*) FROM page WHERE url = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, pageUrl);
            return count != null && count > 0;
        }
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