package searchengine.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "site", schema = "sites_parsing")
public class SiteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statuses statuses;

    @Column(name = "status_time", nullable = false)
    private LocalDateTime localDateTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(nullable = false, length = 255)
    private String url;

    @Column(nullable = false, length = 255)
    private String name;

    @OneToMany(mappedBy = "siteEntity", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PageEntity> pages;

    @OneToMany(mappedBy = "siteEntity", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<LemmaEntity> lemmas;
}
