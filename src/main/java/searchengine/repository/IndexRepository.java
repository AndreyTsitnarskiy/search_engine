package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.IndexEntity;

@Repository
public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {

    @Modifying
    @Query(value = "TRUNCATE TABLE sites_parsing.indexes_table RESTART IDENTITY CASCADE", nativeQuery = true)
    void truncateAllIndexes();

    @Modifying
    @Query(value = "DELETE FROM sites_parsing.indexes_table WHERE page_id = :pageId", nativeQuery = true)
    void deleteAllByPage(@Param("pageId") int pageId);
}
