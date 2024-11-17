package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.entity.IndexEntity;
import searchengine.entity.LemmaEntity;

@Repository
public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {

    int countByLemma(LemmaEntity lemmaEntity);

}
