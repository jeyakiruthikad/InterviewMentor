-- =====================================================================
-- Migration: Job Description Analysis, Resume + JD Matching, Explainable
-- Readiness dimensions, and Career Intelligence change explanations.
--
-- Run this against an EXISTING career_intelligence_db install that was
-- created before this feature set. A brand-new install does not need
-- this file - database/schema.sql already includes everything below.
--
-- This migration is safe to run more than once on MySQL 8.x. MySQL 8.0
-- does not support ALTER TABLE ... ADD COLUMN IF NOT EXISTS in this form,
-- so the column additions are guarded through information_schema checks.
-- =====================================================================

USE career_intelligence_db;

DROP PROCEDURE IF EXISTS migrate_v2_job_intelligence;
DELIMITER $$
CREATE PROCEDURE migrate_v2_job_intelligence()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'readiness_score_history'
          AND column_name = 'technical_dimension'
    ) THEN
        ALTER TABLE readiness_score_history
            ADD COLUMN technical_dimension DECIMAL(5,2) NOT NULL DEFAULT 0 AFTER mock_component;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'readiness_score_history'
          AND column_name = 'problem_solving_dimension'
    ) THEN
        ALTER TABLE readiness_score_history
            ADD COLUMN problem_solving_dimension DECIMAL(5,2) NOT NULL DEFAULT 0 AFTER technical_dimension;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'readiness_score_history'
          AND column_name = 'communication_dimension'
    ) THEN
        ALTER TABLE readiness_score_history
            ADD COLUMN communication_dimension DECIMAL(5,2) NOT NULL DEFAULT 0 AFTER problem_solving_dimension;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'readiness_score_history'
          AND column_name = 'behavioral_dimension'
    ) THEN
        ALTER TABLE readiness_score_history
            ADD COLUMN behavioral_dimension DECIMAL(5,2) NOT NULL DEFAULT 0 AFTER communication_dimension;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'readiness_score_history'
          AND column_name = 'role_alignment_dimension'
    ) THEN
        ALTER TABLE readiness_score_history
            ADD COLUMN role_alignment_dimension DECIMAL(5,2) NOT NULL DEFAULT 0 AFTER behavioral_dimension;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'readiness_score_history'
          AND column_name = 'biggest_strength'
    ) THEN
        ALTER TABLE readiness_score_history
            ADD COLUMN biggest_strength VARCHAR(255) DEFAULT NULL AFTER role_alignment_dimension;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'readiness_score_history'
          AND column_name = 'biggest_risk'
    ) THEN
        ALTER TABLE readiness_score_history
            ADD COLUMN biggest_risk VARCHAR(255) DEFAULT NULL AFTER biggest_strength;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'readiness_score_history'
          AND column_name = 'next_recommended_action'
    ) THEN
        ALTER TABLE readiness_score_history
            ADD COLUMN next_recommended_action VARCHAR(500) DEFAULT NULL AFTER biggest_risk;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'career_refresh_log'
          AND column_name = 'details'
    ) THEN
        ALTER TABLE career_refresh_log
            ADD COLUMN details TEXT DEFAULT NULL AFTER roadmap_id;
    END IF;
END$$
DELIMITER ;

CALL migrate_v2_job_intelligence();
DROP PROCEDURE IF EXISTS migrate_v2_job_intelligence;

CREATE TABLE IF NOT EXISTS job_descriptions (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id               BIGINT NOT NULL,
    title                 VARCHAR(150) DEFAULT NULL,
    raw_text              MEDIUMTEXT NOT NULL,
    required_skills       TEXT DEFAULT NULL,
    preferred_skills      TEXT DEFAULT NULL,
    technologies          TEXT DEFAULT NULL,
    responsibilities      TEXT DEFAULT NULL,
    soft_skills           TEXT DEFAULT NULL,
    experience_required   VARCHAR(255) DEFAULT NULL,
    analyzed_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_jd_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_jd_user_time (user_id, analyzed_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS job_match_results (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_description_id     BIGINT DEFAULT NULL,
    user_id                BIGINT NOT NULL,
    match_score            DECIMAL(5,2) NOT NULL DEFAULT 0,
    strong_match_skills    TEXT DEFAULT NULL,
    partial_match_skills   TEXT DEFAULT NULL,
    weak_evidence_skills   TEXT DEFAULT NULL,
    missing_skills         TEXT DEFAULT NULL,
    high_priority_skills   TEXT DEFAULT NULL,
    summary                VARCHAR(500) DEFAULT NULL,
    computed_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_jmr_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_jmr_jd FOREIGN KEY (job_description_id) REFERENCES job_descriptions(id) ON DELETE SET NULL,
    INDEX idx_jmr_user_time (user_id, computed_at)
) ENGINE=InnoDB;
