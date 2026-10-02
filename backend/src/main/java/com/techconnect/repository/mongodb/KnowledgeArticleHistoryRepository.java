package com.techconnect.repository.mongodb;

import com.techconnect.document.KnowledgeArticleHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeArticleHistoryRepository extends MongoRepository<KnowledgeArticleHistory, String> {

    List<KnowledgeArticleHistory> findByArticleIdOrderByPerformedAtDesc(String articleId);

    void deleteByArticleId(String articleId);
}
