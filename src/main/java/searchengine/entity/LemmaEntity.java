package searchengine.entity;

import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "lemma", schema = "sites_parsing")
public class LemmaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity siteEntity;

    @Column(nullable = false, length = 255)
    private String lemma;

    @Column(nullable = false)
    private int frequency;

    @OneToMany(mappedBy = "lemmaEntity", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<IndexEntity> indices;

}
