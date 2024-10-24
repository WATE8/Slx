package searchengine.controllers;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.Pro;

@RestController
@RequestMapping("/api/pages")
public class PageController {

    @Autowired
    private Pro pro;

    @PostMapping("/process")
    public ResponseEntity<String> processPage(@RequestParam String url, @RequestParam int siteId) {
        try {
            pro.processPage(url, siteId);
            return ResponseEntity.ok("Page processed successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error processing page: " + e.getMessage());
        }
    }
}
