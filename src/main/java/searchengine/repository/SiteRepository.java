package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.SiteEntity;
import java.util.List;
import java.util.Optional;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {

    Optional<List<SiteEntity>> findByName(String name);

    @Transactional
    void deleteSiteEntityByUrl(String url);

    @Query(value = "SELECT * FROM sites_parsing.site WHERE url = :url", nativeQuery = true)
    SiteEntity findSiteEntityByUrl(@Param("url") String url);
}
