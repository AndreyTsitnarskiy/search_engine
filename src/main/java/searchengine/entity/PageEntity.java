package searchengine.entity;

import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "page", schema = "sites_parsing")
public class PageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity siteEntity;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @OneToMany(mappedBy = "pageEntity", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<IndexEntity> indices;


}
