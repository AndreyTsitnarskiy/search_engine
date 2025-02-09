package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.entity.IndexEntity;
import searchengine.entity.PageEntity;
import searchengine.entity.SiteEntity;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {

    @Modifying
    @Query(value = "TRUNCATE TABLE sites_parsing.indexes_table RESTART IDENTITY CASCADE", nativeQuery = true)
    void truncateAllIndexes();

    @Modifying
    @Query(value = "DELETE FROM sites_parsing.indexes_table WHERE page_id = :pageId", nativeQuery = true)
    void deleteAllByPage(@Param("pageId") int pageId);

    @Query("SELECT p FROM IndexEntity i JOIN i.page p WHERE i.lemma.lemma = :lemma AND i.page.site = :site")
    List<PageEntity> findPagesByLemma(@Param("lemma") String lemma, @Param("site") SiteEntity site);

    @Query("SELECT SUM(i.rank) FROM IndexEntity i WHERE i.page = :page AND i.lemma.lemma IN :lemmas")
    Float calculatePageRelevance(@Param("page") PageEntity page, @Param("lemmas") List<String> lemmas);
}
