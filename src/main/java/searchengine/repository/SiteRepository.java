package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.entity.SiteEntity;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Long> {

}
