package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.LemmaEntity;
import searchengine.entity.SiteEntity;

import java.util.List;
import java.util.Set;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {

    @Query("SELECT l FROM LemmaEntity l WHERE l.lemma IN :lemmas AND l.site.id = :siteId")
    List<LemmaEntity> getExistsLemmas(@Param("lemmas") List<String> lemmas, @Param("siteId") int siteId);

    @Modifying
    @Query(value = "DELETE FROM sites_parsing.lemmas", nativeQuery = true)
    void deleteAllLemmas();
}
