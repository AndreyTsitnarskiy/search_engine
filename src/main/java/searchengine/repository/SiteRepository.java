package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.entity.SiteEntity;
import searchengine.entity.Statuses;

import java.util.Optional;
import java.util.Set;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {

    boolean existsByStatus(Statuses status);

    Set<SiteEntity> findAllByStatus(Statuses statuses);

    Optional<SiteEntity> findByUrl(String url);

    boolean existsByIdAndStatus(Integer siteId, Statuses statuses);
}
