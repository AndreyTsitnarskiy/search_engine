package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.entity.PageEntity;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Integer> {

    @Query(value = "SELECT COUNT(*) FROM sites_parsing.page WHERE site_id = :siteId", nativeQuery = true)
    int countPagesBySiteId(@Param("siteId") int siteId);

    @Query(value = "SELECT * FROM sites_parsing.page WHERE site_id = :siteId ORDER BY id LIMIT :batchSize OFFSET :offset", nativeQuery = true)
    List<PageEntity> findPagesBySiteIdWithPagination(@Param("siteId") int siteId, @Param("batchSize") int batchSize, @Param("offset") int offset);
}
