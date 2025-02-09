package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.SiteEntity;
import searchengine.entity.Status;

import java.time.LocalDateTime;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {

    @Modifying
    @Transactional
    @Query("UPDATE SiteEntity s SET s.status = :status, s.statusTime = :statusTime WHERE s.id = :siteId")
    void updateSiteStatus(@Param("siteId") int siteId,
                          @Param("status") Status status,
                          @Param("statusTime") LocalDateTime statusTime);

    @Modifying
    @Transactional
    @Query("UPDATE SiteEntity s SET s.status = :status, s.statusTime = :statusTime, s.lastError = :lastError WHERE s.id = :siteId")
    void updateSiteStatusAndLastError(@Param("siteId") int siteId,
                                      @Param("status") Status status,
                                      @Param("statusTime") LocalDateTime statusTime,
                                      @Param("lastError") String lastError);

    @Modifying
    @Query(value = "TRUNCATE TABLE sites_parsing.site RESTART IDENTITY CASCADE", nativeQuery = true)
    void truncateAllSites();

    @Query("SELECT s FROM SiteEntity s WHERE s.url = :url")
    SiteEntity findByUrl(@Param("url") String url);
}
