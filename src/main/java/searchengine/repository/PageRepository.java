package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;

import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Integer> {

    Optional<PageEntity> findBySiteAndPath(SiteEntity siteEntity, String path);

    boolean existsBySiteAndPath(Optional<SiteEntity> siteId, String path);
}
