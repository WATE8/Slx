package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.StatisticsService;
import searchengine.TextProcessor;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final TextProcessor textProcessor;

    public ApiController(StatisticsService statisticsService, TextProcessor textProcessor) {
        this.statisticsService = statisticsService;
        this.textProcessor = textProcessor;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<Map<String, Object>> indexPage(@RequestParam String url, @RequestParam int siteId) {
        Map<String, Object> response = new HashMap<>();

        if (textProcessor.isValidUrl(url)) {
            Map<String, Object> indexResponse = textProcessor.indexPage(url, siteId);

            if ((boolean) indexResponse.get("result")) {
                response.put("result", true);
            } else {
                response.put("result", false);
                response.put("error", indexResponse.get("error"));
            }

            return new ResponseEntity<>(response, HttpStatus.OK);
        } else {
            response.put("result", false);
            response.put("error", "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
    }
}