package searchengine.services;

import searchengine.model.Index;
import searchengine.model.Page;
import searchengine.model.Lemma;
import searchengine.repository.IndexRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IndexService {

    @Autowired
    private IndexRepository indexRepository;

    public Index saveIndex(Index index) {
        return indexRepository.save(index);
    }

    public List<Index> getAllIndexes() {
        return indexRepository.findAll();
    }

    public Index getIndexById(int id) {
        return indexRepository.findById(id).orElse(null);
    }

    public void deleteIndex(int id) {
        indexRepository.deleteById(id);
    }

    // Метод для создания и сохранения индекса с использованием конструктора
    public Index createAndSaveIndex(Page page, Lemma lemma, float rank) {
        Index index = new Index(page, lemma, rank);
        return saveIndex(index);
    }

    // Добавьте дополнительные методы по необходимости
}
