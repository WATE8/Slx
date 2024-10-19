package searchengine.controllers;

import searchengine.model.Lemma;
import searchengine.services.LemmaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lemmas")
public class LemmaController {

    @Autowired
    private LemmaService lemmaService;

    @PostMapping
    public ResponseEntity<Lemma> saveLemma(@RequestBody Lemma lemma) {
        Lemma savedLemma = lemmaService.saveLemma(lemma);
        return ResponseEntity.ok(savedLemma);
    }

    @GetMapping
    public ResponseEntity<List<Lemma>> getAllLemmas() {
        List<Lemma> lemmas = lemmaService.getAllLemmas();
        return ResponseEntity.ok(lemmas);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Lemma> getLemmaById(@PathVariable int id) {
        Lemma lemma = lemmaService.getLemmaById(id);
        return lemma != null ? ResponseEntity.ok(lemma) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLemma(@PathVariable int id) {
        lemmaService.deleteLemma(id);
        return ResponseEntity.noContent().build();
    }
}
