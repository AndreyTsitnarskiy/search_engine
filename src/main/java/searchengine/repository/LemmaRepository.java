package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.entity.LemmaEntity;
import searchengine.entity.SiteEntity;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Long> {

    LemmaEntity findByLemma(String lemma);

    List<LemmaEntity> findAllBySiteEntity(SiteEntity siteEntity);

    LemmaEntity findByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);
}
