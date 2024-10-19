package searchengine.controllers;

import searchengine.model.Index;
import searchengine.services.IndexService;
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
    public ResponseEntity<Index> saveIndex(@RequestBody Index index) {
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
}
