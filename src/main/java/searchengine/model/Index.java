package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "index")
@Data
public class Index {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "page_id", nullable = false)
    private int pageId;

    @Column(name = "lemma_id", nullable = false)
    private int lemmaId;

    @Column(name = "`rank`", nullable = false)
    private float rank;
}
