package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.SiteEntity;
import searchengine.entity.Status;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {

    @Modifying
    @Transactional
    @Query("UPDATE SiteEntity s SET s.status = :status, s.lastError = :error, s.statusTime = CURRENT_TIMESTAMP WHERE s.id = :siteId")
    void updateStatus(@Param("siteId") int siteId, @Param("status") Status status, @Param("error") String error);

    @Modifying
    @Transactional
    @Query("UPDATE SiteEntity s SET s.status = 'FAILED', s.lastError = :error, s.statusTime = CURRENT_TIMESTAMP WHERE s.status = 'INDEXING'")
    void updateAllFailed(@Param("error") String error);
}
