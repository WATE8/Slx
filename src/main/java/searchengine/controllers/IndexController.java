package searchengine.controllers;

import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.services.IndexService;
import lombok.Data; // Не забудьте импортировать Lombok @Data
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/indexes")
public class IndexController {

    @Autowired
    private IndexService indexService;

    @PostMapping
    public ResponseEntity<Index> saveIndex(@RequestBody CreateIndexRequest request) {
        Page page = request.getPage();
        Lemma lemma = request.getLemma();
        float rank = request.getRank();
        Index index = new Index(page, lemma, rank);
        Index savedIndex = indexService.saveIndex(index);
        return ResponseEntity.ok(savedIndex);
    }

    @GetMapping
    public ResponseEntity<List<Index>> getAllIndexes() {
        List<Index> indexes = indexService.getAllIndexes();
        return ResponseEntity.ok(indexes);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Index> getIndexById(@PathVariable int id) {
        Index index = indexService.getIndexById(id);
        return index != null ? ResponseEntity.ok(index) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIndex(@PathVariable int id) {
        indexService.deleteIndex(id);
        return ResponseEntity.noContent().build();
    }

    @Data
    public static class CreateIndexRequest {
        private Page page;
        private Lemma lemma;
        private float rank;
    }
}
