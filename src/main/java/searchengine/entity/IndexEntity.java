package searchengine.entity;

import jakarta.persistence.*;
import lombok.*;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Entity
@Table(name = "indexes_table", schema = "sites_parsing", uniqueConstraints = @UniqueConstraint(columnNames = {"page_id", "lemma_id"}))
public class IndexEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @NonNull
    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false)
    private PageEntity page;

    @NonNull
    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "lemma_id", nullable = false)
    private LemmaEntity lemma;

    @NonNull
    @Column(nullable = false, name = "rank")
    private float rank;
}
