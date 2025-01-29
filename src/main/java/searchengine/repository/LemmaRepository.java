package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.entity.LemmaEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {

    @Modifying
    @Query(value = "TRUNCATE TABLE sites_parsing.lemmas RESTART IDENTITY CASCADE", nativeQuery = true)
    void truncateAllLemmas();

    Optional<LemmaEntity> findBySite_IdAndLemma(Integer siteId, String lemma);

    @Modifying
    @Query("UPDATE LemmaEntity l SET l.frequency = l.frequency + 1 WHERE l.site.id = :siteId AND l.lemma = :lemma")
    void incrementFrequency(@Param("siteId") int siteId, @Param("lemma") String lemma);

    @Query("SELECT l FROM LemmaEntity l JOIN IndexEntity it ON l.id = it.lemma.id WHERE it.page.id = :pageId")
    List<LemmaEntity> findUnusedLemmasBySite(@Param("pageId") int pageId);
}
