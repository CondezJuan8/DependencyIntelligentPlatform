ALTER TABLE local_repository_sources
    DROP CONSTRAINT local_repository_sources_file_count_check;

ALTER TABLE local_repository_sources
    ADD CONSTRAINT local_repository_sources_file_count_check
    CHECK (file_count BETWEEN 1 AND 50000);
