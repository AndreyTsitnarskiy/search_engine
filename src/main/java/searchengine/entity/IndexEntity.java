package searchengine.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "index_table", schema = "sites_parsing")
public class IndexEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false)
    private PageEntity pageEntity;

    @ManyToOne
    @JoinColumn(name = "lemma_id", nullable = false)
    private LemmaEntity lemmaEntity;

    @Column(nullable = false)
    private float rank;
}
