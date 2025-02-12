package searchengine.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "page", schema = "sites_parsing")
public class PageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(cascade = CascadeType.MERGE, fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(columnDefinition = "TEXT")
    private String content;
}
