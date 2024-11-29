package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.LemmaEntity;
import searchengine.entity.SiteEntity;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {

    @Modifying
    @Transactional
    @Query(value = "UPDATE sites_parsing.lemmas SET frequency = frequency - 1 WHERE site_id = :siteId AND lemma IN :lemmas",
            nativeQuery = true)
    void reduceByOneLemmaFrequencies(@Param("siteId") int siteId, @Param("lemmas") Collection<String> lemmas);

    @Query("SELECT l FROM LemmaEntity l WHERE l.site.id = :siteId AND l.lemma IN :lemmas")
    List<LemmaEntity> findBySiteIdAndLemmaIn(@Param("siteId") Integer siteId, @Param("lemmas") Set<String> lemmas);

    int countLemmasEntitiesBySite(SiteEntity siteEntity);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM sites_parsing.lemmas WHERE site_id = :siteId AND frequency < 1", nativeQuery = true)
    void deleteLemmasWithNoFrequencies(@Param("siteId") int siteId);

    @Query(value = "SELECT * FROM sites_parsing.lemmas WHERE lemma IN :queryWords order by frequency asc", nativeQuery = true)
    List<LemmaEntity> findAllByLemmaName(@Param("queryWords") Set<String> queryWords);

    @Query(value = "SELECT * FROM sites_parsing.lemmas WHERE lemma IN :lemmaEntities and site_id IN :siteId", nativeQuery = true)
    List<LemmaEntity> findAllByLemmaNameAndSiteName(@Param("lemmaEntities") List<String> lemmaEntities,
                                                    @Param("siteId") List<Integer> siteEntities);

    @Query(value = "SELECT * FROM sites_parsing.lemmas WHERE lemma IN :queryWords " +
            "ORDER By frequency LIMIT 1", nativeQuery = true)
    LemmaEntity findByMinFrequency(@Param("queryWords") List<String> queryWords);

}
