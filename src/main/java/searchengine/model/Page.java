package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "page",
        indexes = {@jakarta.persistence.Index(name = "idx_path", columnList = "path")},
        uniqueConstraints = {@UniqueConstraint(columnNames = {"site_id", "path"})})  // Уникальность для каждой пары site + path
@Getter
@Setter
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    // Связь с таблицей Site с каскадированием
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)  // Позволяет автоматическое сохранение сайта при создании страницы
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    // Уникальный путь для каждой страницы в пределах одного сайта
    @Column(name = "path", columnDefinition = "TEXT", nullable = false)
    private String path;

    // Код статуса страницы (например, 200, 404 и т.д.)
    @Column(name = "code", nullable = false)
    private int code;

    // Содержимое страницы
    @Column(name = "content", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;

    // Удобный конструктор
    public Page(Site site, String path, int code, String content) {
        this.site = site;
        this.path = path;
        this.code = code;
        this.content = content;
    }

    // Пустой конструктор для JPA
    public Page() {}
}