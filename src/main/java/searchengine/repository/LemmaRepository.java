package searchengine.repository;

import searchengine.model.Lemma;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    // Могут быть добавлены методы для поиска по полям
}
