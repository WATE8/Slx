package searchengine.services;

import searchengine.model.Lemma;
import searchengine.repository.LemmaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LemmaService {

    @Autowired
    private LemmaRepository lemmaRepository;

    public Lemma saveLemma(Lemma lemma) {
        return lemmaRepository.save(lemma);
    }

    public List<Lemma> getAllLemmas() {
        return lemmaRepository.findAll();
    }

    public Lemma getLemmaById(int id) {
        return lemmaRepository.findById(id).orElse(null);
    }

    public void deleteLemma(int id) {
        lemmaRepository.deleteById(id);
    }

    // Добавьте дополнительные методы по необходимости
}
