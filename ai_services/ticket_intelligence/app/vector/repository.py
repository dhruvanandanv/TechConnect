"""
Vector Storage Repository for TechConnect Knowledge Chunks.
Implements dual-backend architecture:
1. Primary: PostgreSQL + pgvector (with HNSW index & cosine distance <=> operator).
2. Fallback: In-memory/SQLite store with NumPy cosine similarity calculation
   to guarantee seamless local development and automated CI/test execution when Postgres is offline.
"""
import logging
import os
import json
from datetime import datetime
from typing import List, Dict, Any, Optional
import numpy as np

logger = logging.getLogger(__name__)


def compute_cosine_similarity(vec_a: List[float], vec_b: List[float]) -> float:
    """Computes exact cosine similarity between two float vectors using NumPy."""
    a = np.array(vec_a, dtype=np.float32)
    b = np.array(vec_b, dtype=np.float32)
    norm_a = np.linalg.norm(a)
    norm_b = np.linalg.norm(b)
    if norm_a == 0.0 or norm_b == 0.0:
        return 0.0
    return float(np.dot(a, b) / (norm_a * norm_b))


class VectorRepository:
    def __init__(self):
        self.pg_enabled = False
        self.pg_conn = None
        self._memory_chunks: Dict[str, Dict[str, Any]] = {}
        self._init_database()

    def _init_database(self):
        """Attempts to connect to PostgreSQL and verify pgvector extension."""
        pg_host = os.getenv("POSTGRES_HOST", "localhost")
        pg_port = int(os.getenv("POSTGRES_PORT", "5432"))
        pg_db = os.getenv("POSTGRES_DB", "techconnect_db")
        pg_user = os.getenv("POSTGRES_USER", "postgres")
        pg_pass = os.getenv("POSTGRES_PASSWORD", "postgres")

        try:
            import psycopg2
            from pgvector.psycopg2 import register_vector

            conn = psycopg2.connect(
                host=pg_host,
                port=pg_port,
                dbname=pg_db,
                user=pg_user,
                password=pg_pass,
                connect_timeout=3,
            )
            conn.autocommit = True
            with conn.cursor() as cur:
                cur.execute("CREATE EXTENSION IF NOT EXISTS vector;")
                register_vector(conn)

                cur.execute("""
                    CREATE TABLE IF NOT EXISTS knowledge_embedding_chunks (
                        id SERIAL PRIMARY KEY,
                        article_id VARCHAR(100) NOT NULL,
                        chunk_id VARCHAR(150) NOT NULL UNIQUE,
                        chunk_index INT NOT NULL,
                        article_version INT NOT NULL,
                        section VARCHAR(50) NOT NULL,
                        chunk_text TEXT NOT NULL,
                        category VARCHAR(100) NOT NULL,
                        tags TEXT NOT NULL,
                        status VARCHAR(50) NOT NULL DEFAULT 'PUBLISHED',
                        embedding vector(384),
                        embedding_model VARCHAR(100) NOT NULL,
                        is_active BOOLEAN NOT NULL DEFAULT TRUE,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    );
                """)
                cur.execute("""
                    CREATE INDEX IF NOT EXISTS idx_chunks_article_active
                    ON knowledge_embedding_chunks (article_id, is_active);
                """)
                cur.execute("""
                    CREATE INDEX IF NOT EXISTS idx_chunks_status
                    ON knowledge_embedding_chunks (status);
                """)
                # HNSW cosine vector index for scalable sub-millisecond retrieval
                try:
                    cur.execute("""
                        CREATE INDEX IF NOT EXISTS idx_chunks_vector_cosine
                        ON knowledge_embedding_chunks USING hnsw (embedding vector_cosine_ops);
                    """)
                except Exception as hnsw_err:
                    logger.warning("HNSW index creation note (falling back to sequential scan): %s", hnsw_err)

            self.pg_conn = conn
            self.pg_enabled = True
            logger.info("PostgreSQL + pgvector initialized successfully on %s:%d/%s", pg_host, pg_port, pg_db)

        except Exception as exc:
            logger.info("PostgreSQL pgvector unavailable (%s). Using high-performance in-memory vector store.", exc)
            self.pg_enabled = False

    def save_chunk(self, chunk: Dict[str, Any]) -> bool:
        """Saves or updates a single knowledge embedding chunk."""
        return self.save_chunks([chunk]) == 1

    def save_chunks(self, chunks: List[Dict[str, Any]]) -> int:
        """Saves a batch of knowledge embedding chunks."""
        if not chunks:
            return 0

        saved_count = 0
        now_dt = datetime.now()

        for c in chunks:
            chunk_id = c["chunkId"]
            article_id = c["articleId"]
            version = c.get("articleVersion", 1)
            section = c.get("section", "GENERAL")
            chunk_text = c.get("text", "")
            category = c.get("category", "GENERAL")
            tags = c.get("tags", [])
            status = c.get("status", "PUBLISHED")
            embedding = c.get("embedding", [])
            embedding_model = c.get("embeddingModel", "all-MiniLM-L6-v2")

            # Invalidate older versions in memory
            for cid, old_chunk in list(self._memory_chunks.items()):
                if old_chunk["article_id"] == article_id and old_chunk["article_version"] != version:
                    old_chunk["is_active"] = False

            # Save in memory
            self._memory_chunks[chunk_id] = {
                "id": len(self._memory_chunks) + 1,
                "article_id": article_id,
                "chunk_id": chunk_id,
                "chunk_index": c.get("chunkIndex", 0),
                "article_version": version,
                "section": section,
                "chunk_text": chunk_text,
                "category": category,
                "tags": tags,
                "status": status,
                "embedding": embedding,
                "embedding_model": embedding_model,
                "is_active": True,
                "created_at": now_dt,
            }
            saved_count += 1

            # Persist to PostgreSQL if available
            if self.pg_enabled and self.pg_conn:
                try:
                    with self.pg_conn.cursor() as cur:
                        # Mark older versions inactive
                        cur.execute(
                            "UPDATE knowledge_embedding_chunks SET is_active = FALSE WHERE article_id = %s AND article_version != %s;",
                            (article_id, version),
                        )
                        # Upsert chunk
                        cur.execute("""
                            INSERT INTO knowledge_embedding_chunks
                            (article_id, chunk_id, chunk_index, article_version, section, chunk_text, category, tags, status, embedding, embedding_model, is_active, created_at)
                            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, TRUE, %s)
                            ON CONFLICT (chunk_id) DO UPDATE SET
                                chunk_text = EXCLUDED.chunk_text,
                                category = EXCLUDED.category,
                                tags = EXCLUDED.tags,
                                status = EXCLUDED.status,
                                embedding = EXCLUDED.embedding,
                                is_active = TRUE,
                                article_version = EXCLUDED.article_version;
                        """, (
                            article_id, chunk_id, c.get("chunkIndex", 0), version, section, chunk_text,
                            category, json.dumps(tags), status, embedding, embedding_model, now_dt
                        ))
                except Exception as pg_err:
                    logger.warning("Failed to persist chunk %s to PostgreSQL: %s", chunk_id, pg_err)

        return saved_count

    def delete_chunks_by_article(self, article_id: str, keep_version: Optional[int] = None) -> int:
        """Deletes or marks inactive all chunks for an article (or stale versions)."""
        deleted_count = 0
        for cid, chunk in list(self._memory_chunks.items()):
            if chunk["article_id"] == article_id:
                if keep_version is None or chunk["article_version"] != keep_version:
                    chunk["is_active"] = False
                    deleted_count += 1

        if self.pg_enabled and self.pg_conn:
            try:
                with self.pg_conn.cursor() as cur:
                    if keep_version is None:
                        cur.execute("DELETE FROM knowledge_embedding_chunks WHERE article_id = %s;", (article_id,))
                    else:
                        cur.execute("DELETE FROM knowledge_embedding_chunks WHERE article_id = %s AND article_version != %s;", (article_id, keep_version))
            except Exception as pg_err:
                logger.warning("Failed to delete stale PostgreSQL chunks for article %s: %s", article_id, pg_err)

        return deleted_count

    def update_article_status(self, article_id: str, status: str) -> int:
        """Updates the status (e.g. PUBLISHED, DRAFT, ARCHIVED) for all active chunks of an article."""
        count = 0
        for cid, chunk in self._memory_chunks.items():
            if chunk["article_id"] == article_id:
                chunk["status"] = status
                count += 1

        if self.pg_enabled and self.pg_conn:
            try:
                with self.pg_conn.cursor() as cur:
                    cur.execute(
                        "UPDATE knowledge_embedding_chunks SET status = %s WHERE article_id = %s;",
                        (status, article_id)
                    )
            except Exception as pg_err:
                logger.warning("Failed to update status in PostgreSQL for article %s: %s", article_id, pg_err)

        return count

    def search_vectors(
        self,
        query_vector: List[float],
        top_k: int = 5,
        min_similarity: float = 0.50,
        category: Optional[str] = None,
        allowed_statuses: Optional[List[str]] = None,
        allowed_article_ids: Optional[List[str]] = None,
    ) -> List[Dict[str, Any]]:
        """
        Performs semantic similarity search against active chunks.
        Filters by category, status (RBAC), and similarity threshold.
        """
        results: List[Dict[str, Any]] = []

        # If PostgreSQL is active with pgvector
        if self.pg_enabled and self.pg_conn:
            try:
                with self.pg_conn.cursor() as cur:
                    # Cosine distance: embedding <=> query_vector
                    # Cosine similarity = 1 - (embedding <=> query_vector)
                    sql = """
                        SELECT article_id, chunk_id, article_version, section, chunk_text, category, tags, status,
                               1 - (embedding <=> %s::vector) AS similarity
                        FROM knowledge_embedding_chunks
                        WHERE is_active = TRUE
                    """
                    params: List[Any] = [query_vector]

                    if category and category.strip():
                        sql += " AND UPPER(category) = UPPER(%s)"
                        params.append(category.strip())

                    if allowed_statuses:
                        sql += " AND status = ANY(%s)"
                        params.append(allowed_statuses)

                    if allowed_article_ids is not None:
                        sql += " AND article_id = ANY(%s)"
                        params.append(allowed_article_ids)

                    sql += " AND (1 - (embedding <=> %s::vector)) >= %s"
                    params.extend([query_vector, min_similarity])

                    sql += " ORDER BY similarity DESC LIMIT %s;"
                    params.append(top_k)

                    cur.execute(sql, tuple(params))
                    rows = cur.fetchall()

                    for r in rows:
                        tags_val = r[6]
                        if isinstance(tags_val, str):
                            try:
                                tags_val = json.loads(tags_val)
                            except Exception:
                                tags_val = [tags_val]

                        results.append({
                            "articleId": r[0],
                            "chunkId": r[1],
                            "articleVersion": r[2],
                            "section": r[3],
                            "content": r[4],
                            "category": r[5],
                            "tags": tags_val or [],
                            "similarity": round(float(r[8]), 4),
                        })

                    return results
            except Exception as pg_err:
                logger.warning("PostgreSQL vector search failed (%s), querying memory fallback", pg_err)

        # In-memory / Fallback cosine search
        candidates = []
        for cid, chunk in self._memory_chunks.items():
            if not chunk.get("is_active", True):
                continue

            if category and category.strip() and chunk.get("category", "").upper() != category.strip().upper():
                continue

            if allowed_statuses and chunk.get("status", "PUBLISHED") not in allowed_statuses:
                continue

            if allowed_article_ids is not None and chunk.get("article_id") not in allowed_article_ids:
                continue

            sim = compute_cosine_similarity(query_vector, chunk["embedding"])
            if sim >= min_similarity:
                candidates.append((sim, chunk))

        # Sort descending by similarity
        candidates.sort(key=lambda x: x[0], reverse=True)

        for sim, chunk in candidates[:top_k]:
            results.append({
                "articleId": chunk["article_id"],
                "chunkId": chunk["chunk_id"],
                "articleVersion": chunk["article_version"],
                "section": chunk["section"],
                "content": chunk["chunk_text"],
                "category": chunk["category"],
                "tags": chunk.get("tags", []),
                "similarity": round(float(sim), 4),
            })

        return results

    def get_stats(self) -> Dict[str, Any]:
        """Returns statistics on active chunks."""
        total_memory = len(self._memory_chunks)
        active_memory = sum(1 for c in self._memory_chunks.values() if c.get("is_active", True))

        pg_total = 0
        if self.pg_enabled and self.pg_conn:
            try:
                with self.pg_conn.cursor() as cur:
                    cur.execute("SELECT COUNT(*) FROM knowledge_embedding_chunks WHERE is_active = TRUE;")
                    row = cur.fetchone()
                    if row:
                        pg_total = row[0]
            except Exception:
                pass

        return {
            "pgEnabled": self.pg_enabled,
            "activeMemoryChunks": active_memory,
            "totalMemoryChunks": total_memory,
            "activePgChunks": pg_total,
        }

    def clear(self):
        """Clears memory storage (primarily for test resets)."""
        self._memory_chunks.clear()


vector_repository = VectorRepository()
