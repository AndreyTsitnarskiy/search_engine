package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.entity.LemmaEntity;
import searchengine.entity.SiteEntity;

import java.util.Optional;
import java.util.Set;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {

    Optional<LemmaEntity> findBySiteAndLemma(SiteEntity siteEntity, String lemma);

    Set<LemmaEntity> findAllBySite(SiteEntity siteEntity);

}
