package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Integer> {

    @Query(value = "SELECT COUNT(*) FROM sites_parsing.page WHERE site_id = :siteId", nativeQuery = true)
    int countPagesBySiteId(@Param("siteId") int siteId);

    @Query(value = "SELECT * FROM sites_parsing.page WHERE site_id = :siteId ORDER BY id LIMIT :batchSize OFFSET :offset", nativeQuery = true)
    List<PageEntity> findPagesBySiteIdWithPagination(@Param("siteId") int siteId, @Param("batchSize") int batchSize, @Param("offset") int offset);

    @Modifying
    @Query(value = "TRUNCATE TABLE sites_parsing.page RESTART IDENTITY CASCADE", nativeQuery = true)
    void truncateAllPages();

    @Query("SELECT p FROM PageEntity p WHERE p.site.id = :siteId AND p.path = :uri")
    Optional<PageEntity> findBySiteAndPath(@Param("siteId") int siteEntity, @Param("uri") String uri);

    int countPageEntitiesBySite(SiteEntity siteEntity);

    @Query("SELECT p FROM PageEntity p " +
            "JOIN IndexEntity i ON p.id = i.page.id " +
            "JOIN LemmaEntity l ON i.lemma.id = l.id " +
            "WHERE l.lemma IN :lemmas " +
            "GROUP BY p.id " +
            "HAVING COUNT(DISTINCT l.lemma) = :lemmaCount " +
            "ORDER BY SUM(i.rank) DESC " +
            "LIMIT :maxResults")
    List<PageEntity> findPagesMatchingLemmas(@Param("lemmas") List<String> lemmas,
                                             @Param("lemmaCount") int lemmaCount,
                                             @Param("maxResults") int maxResults);

    @Query("SELECT COUNT(p) FROM PageEntity p")
    int countTotalPages();
}
