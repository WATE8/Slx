package searchengine.repository;

import searchengine.model.Index;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {
    // Дополнительные методы можно добавить по необходимости
}