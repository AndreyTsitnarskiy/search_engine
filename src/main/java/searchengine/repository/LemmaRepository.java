package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.entity.LemmaEntity;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {
    @Query("SELECT l FROM LemmaEntity l WHERE l.lemma IN :lemmas AND l.site.id = :siteId")
    List<LemmaEntity> getExistsLemmas(@Param("lemmas") List<String> lemmas, @Param("siteId") int siteId);
}
