-- =====================================================================
-- InterviewMentor
-- Database Schema (MySQL 8.x)
-- =====================================================================
-- Run this once to create the database and all tables:
--   mysql -u root -p < database/schema.sql
-- =====================================================================

DROP DATABASE IF EXISTS career_intelligence_db;
CREATE DATABASE career_intelligence_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE career_intelligence_db;

-- ---------------------------------------------------------------------
-- USERS
-- ---------------------------------------------------------------------
CREATE TABLE users (
    user_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(120) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    password_salt   VARCHAR(64)  NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    phone           VARCHAR(20)  DEFAULT NULL,
    role            ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at   TIMESTAMP NULL DEFAULT NULL,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until    TIMESTAMP NULL DEFAULT NULL
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- USER PROFILE (extended info; also used later for resume/target role)
-- ---------------------------------------------------------------------
CREATE TABLE user_profiles (
    profile_id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    target_role         VARCHAR(120) DEFAULT NULL,
    target_company      VARCHAR(120) DEFAULT NULL,
    experience_level    ENUM('FRESHER','JUNIOR','MID','SENIOR') DEFAULT 'FRESHER',
    bio                 TEXT DEFAULT NULL,
    resume_path         VARCHAR(255) DEFAULT NULL,
    resume_extracted_skills TEXT DEFAULT NULL,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE KEY uq_profile_user (user_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- TOPICS
-- ---------------------------------------------------------------------
CREATE TABLE topics (
    topic_id        INT AUTO_INCREMENT PRIMARY KEY,
    topic_name      VARCHAR(100) NOT NULL UNIQUE,
    category        VARCHAR(80)  DEFAULT 'General',
    description     VARCHAR(255) DEFAULT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- QUESTIONS
-- ---------------------------------------------------------------------
CREATE TABLE questions (
    question_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic_id        INT NOT NULL,
    question_text   TEXT NOT NULL,
    question_type   ENUM('MCQ','TRUE_FALSE','DESCRIPTIVE') NOT NULL,
    difficulty      ENUM('EASY','MEDIUM','HARD') NOT NULL DEFAULT 'EASY',
    correct_answer  VARCHAR(500) DEFAULT NULL,   -- for TRUE_FALSE ('TRUE'/'FALSE') and MCQ (option letter), model answer for DESCRIPTIVE
    explanation     TEXT DEFAULT NULL,
    marks           INT NOT NULL DEFAULT 1,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_question_topic FOREIGN KEY (topic_id) REFERENCES topics(topic_id) ON DELETE CASCADE,
    INDEX idx_question_topic_diff (topic_id, difficulty),
    INDEX idx_question_type (question_type)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- QUESTION OPTIONS (for MCQ questions)
-- ---------------------------------------------------------------------
CREATE TABLE question_options (
    option_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id     BIGINT NOT NULL,
    option_label    CHAR(1) NOT NULL,      -- A, B, C, D
    option_text     VARCHAR(500) NOT NULL,
    is_correct      BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_option_question FOREIGN KEY (question_id) REFERENCES questions(question_id) ON DELETE CASCADE,
    UNIQUE KEY uq_question_label (question_id, option_label)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- ASSESSMENTS (a single attempt/session by a user)
-- ---------------------------------------------------------------------
CREATE TABLE assessments (
    assessment_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT NOT NULL,
    title             VARCHAR(150) NOT NULL,
    total_questions   INT NOT NULL,
    duration_minutes  INT NOT NULL,
    status            ENUM('IN_PROGRESS','COMPLETED','AUTO_SUBMITTED','ABANDONED') NOT NULL DEFAULT 'IN_PROGRESS',
    mode              ENUM('STANDARD','ADAPTIVE','RETRY','RESUME') NOT NULL DEFAULT 'STANDARD',
    source_assessment_id BIGINT DEFAULT NULL,
    total_score       DECIMAL(6,2) DEFAULT 0.00,
    max_score         DECIMAL(6,2) DEFAULT 0.00,
    correct_count     INT DEFAULT 0,
    wrong_count       INT DEFAULT 0,
    unanswered_count  INT DEFAULT 0,
    start_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time          TIMESTAMP NULL DEFAULT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_assessment_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_assessment_source FOREIGN KEY (source_assessment_id) REFERENCES assessments(assessment_id) ON DELETE SET NULL,
    INDEX idx_assessment_user (user_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- ASSESSMENT <-> TOPICS (many-to-many: an assessment can span topics)
-- ---------------------------------------------------------------------
CREATE TABLE assessment_topics (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    assessment_id   BIGINT NOT NULL,
    topic_id        INT NOT NULL,
    CONSTRAINT fk_at_assessment FOREIGN KEY (assessment_id) REFERENCES assessments(assessment_id) ON DELETE CASCADE,
    CONSTRAINT fk_at_topic FOREIGN KEY (topic_id) REFERENCES topics(topic_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- ASSESSMENT QUESTIONS (the fixed, randomized set of questions drawn
-- for one assessment attempt, in presentation order)
-- ---------------------------------------------------------------------
CREATE TABLE assessment_questions (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    assessment_id     BIGINT NOT NULL,
    question_id       BIGINT NOT NULL,
    question_order    INT NOT NULL,
    CONSTRAINT fk_aq_assessment FOREIGN KEY (assessment_id) REFERENCES assessments(assessment_id) ON DELETE CASCADE,
    CONSTRAINT fk_aq_question FOREIGN KEY (question_id) REFERENCES questions(question_id) ON DELETE CASCADE,
    UNIQUE KEY uq_assessment_order (assessment_id, question_order)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- USER ANSWERS
-- ---------------------------------------------------------------------
CREATE TABLE user_answers (
    answer_id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    assessment_id       BIGINT NOT NULL,
    question_id         BIGINT NOT NULL,
    selected_option_id  BIGINT DEFAULT NULL,      -- for MCQ
    answer_text         TEXT DEFAULT NULL,        -- for TRUE_FALSE ('TRUE'/'FALSE') and DESCRIPTIVE free text
    is_correct          BOOLEAN DEFAULT NULL,     -- NULL until graded (should always be graded post current implementation)
    marks_obtained      DECIMAL(6,2) DEFAULT 0.00,
    ai_score            INT DEFAULT NULL,         -- 0-100 semantic evaluation score (DESCRIPTIVE answers only)
    ai_feedback         TEXT DEFAULT NULL,        -- AI-generated correct/missing/incorrect points + improvement suggestions
    answered_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_answer_assessment FOREIGN KEY (assessment_id) REFERENCES assessments(assessment_id) ON DELETE CASCADE,
    CONSTRAINT fk_answer_question FOREIGN KEY (question_id) REFERENCES questions(question_id) ON DELETE CASCADE,
    CONSTRAINT fk_answer_option FOREIGN KEY (selected_option_id) REFERENCES question_options(option_id) ON DELETE SET NULL,
    UNIQUE KEY uq_assessment_question (assessment_id, question_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- TOPIC PERFORMANCE (per-user, per-topic running accuracy - drives the
-- adaptive difficulty engine and the performance dashboard)
-- ---------------------------------------------------------------------
CREATE TABLE topic_performance (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT NOT NULL,
    topic_id          INT NOT NULL,
    attempts_count    INT NOT NULL DEFAULT 0,
    correct_count     INT NOT NULL DEFAULT 0,
    wrong_count       INT NOT NULL DEFAULT 0,
    accuracy_percent  DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    current_difficulty ENUM('EASY','MEDIUM','HARD') NOT NULL DEFAULT 'EASY',
    recent_results    VARCHAR(20) NOT NULL DEFAULT '',
    last_attempt_at   TIMESTAMP NULL DEFAULT NULL,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tp_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_tp_topic FOREIGN KEY (topic_id) REFERENCES topics(topic_id) ON DELETE CASCADE,
    UNIQUE KEY uq_user_topic (user_id, topic_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- MISTAKE LOG (per-user, per-question wrong-answer tracking - powers the
-- mistake analyzer and the "retry incorrect questions" flow)
-- ---------------------------------------------------------------------
CREATE TABLE mistake_log (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT NOT NULL,
    question_id       BIGINT NOT NULL,
    topic_id          INT NOT NULL,
    times_wrong       INT NOT NULL DEFAULT 1,
    resolved          BOOLEAN NOT NULL DEFAULT FALSE,
    last_wrong_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at       TIMESTAMP NULL DEFAULT NULL,
    CONSTRAINT fk_mistake_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_mistake_question FOREIGN KEY (question_id) REFERENCES questions(question_id) ON DELETE CASCADE,
    CONSTRAINT fk_mistake_topic FOREIGN KEY (topic_id) REFERENCES topics(topic_id) ON DELETE CASCADE,
    UNIQUE KEY uq_user_question (user_id, question_id),
    INDEX idx_mistake_user_resolved (user_id, resolved)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Reserved tables are created as part of the complete schema so a fresh installation is consistent;
-- not yet populated until the corresponding feature is used).
-- ---------------------------------------------------------------------
CREATE TABLE user_badges (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    badge_name      VARCHAR(100) NOT NULL,
    badge_code      VARCHAR(50) DEFAULT NULL,
    description     VARCHAR(255) DEFAULT NULL,
    earned_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_badge_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE KEY uq_user_badge_code (user_id, badge_code)
) ENGINE=InnoDB;

CREATE TABLE mock_interview_sessions (
    session_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    role_name       VARCHAR(120) DEFAULT NULL,
    company_name    VARCHAR(120) DEFAULT NULL,
    topic_ids       VARCHAR(255) DEFAULT NULL,
    readiness_score DECIMAL(5,2) DEFAULT NULL,
    status          ENUM('IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'IN_PROGRESS',
    total_questions INT NOT NULL DEFAULT 0,
    average_score   DECIMAL(5,2) DEFAULT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP NULL DEFAULT NULL,
    CONSTRAINT fk_mock_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ===========================================================================
-- RESUME, PERFORMANCE AND READINESS FEATURES
-- Resume AI Analysis, Resume-Based Questions, AI Mock Interview follow-ups,
-- and the Interview Readiness Score. The complete schema is provided here so
-- fresh installations do not require historical migration scripts.
-- database; this file is the full from-scratch schema for a fresh install.
-- ===========================================================================

-- One AI-extracted, categorised skill from a user's uploaded resume,
-- optionally matched to a `topics` row (by name) so it can drive
-- resume-based question selection and weak-skill detection.
CREATE TABLE resume_skills (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    skill_name       VARCHAR(100) NOT NULL,
    category         VARCHAR(80) NOT NULL DEFAULT 'General',
    matched_topic_id INT DEFAULT NULL,
    extracted_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rskill_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_rskill_topic FOREIGN KEY (matched_topic_id) REFERENCES topics(topic_id) ON DELETE SET NULL,
    UNIQUE KEY uq_user_skill (user_id, skill_name)
) ENGINE=InnoDB;

-- Reference taxonomy of skills expected for a given target role, used to
-- compute "missing skills" (role requirements not present in the user's
-- extracted resume skills) and the resume-coverage readiness component.
CREATE TABLE role_skill_requirements (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name   VARCHAR(120) NOT NULL,
    skill_name  VARCHAR(100) NOT NULL,
    category    VARCHAR(80) NOT NULL DEFAULT 'General',
    priority_weight TINYINT NOT NULL DEFAULT 5,
    UNIQUE KEY uq_role_skill (role_name, skill_name)
) ENGINE=InnoDB;

-- Each question (original or AI-generated dynamic follow-up) asked within
-- an AI mock interview session, with the candidate's answer and its AI
-- evaluation once answered.
CREATE TABLE mock_interview_questions (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id          BIGINT NOT NULL,
    question_order      INT NOT NULL,
    question_text       TEXT NOT NULL,
    is_followup         BOOLEAN NOT NULL DEFAULT FALSE,
    parent_question_id  BIGINT DEFAULT NULL,
    answer_text         TEXT DEFAULT NULL,
    ai_score            INT DEFAULT NULL,
    ai_feedback         TEXT DEFAULT NULL,
    answered_at         TIMESTAMP NULL DEFAULT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_miq_session FOREIGN KEY (session_id) REFERENCES mock_interview_sessions(session_id) ON DELETE CASCADE,
    CONSTRAINT fk_miq_parent FOREIGN KEY (parent_question_id) REFERENCES mock_interview_questions(id) ON DELETE SET NULL,
    INDEX idx_miq_session (session_id)
) ENGINE=InnoDB;

-- Snapshot of every computed Interview Readiness Score, so the UI can show
-- a trend over time instead of just the latest number.
CREATE TABLE readiness_score_history (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                BIGINT NOT NULL,
    overall_score          DECIMAL(5,2) NOT NULL,
    assessment_component   DECIMAL(5,2) NOT NULL DEFAULT 0,
    topic_component        DECIMAL(5,2) NOT NULL DEFAULT 0,
    mistake_component      DECIMAL(5,2) NOT NULL DEFAULT 0,
    resume_component       DECIMAL(5,2) NOT NULL DEFAULT 0,
    mock_component         DECIMAL(5,2) NOT NULL DEFAULT 0,
    -- Explainable Readiness dimensions (final release addition): the same underlying
    -- signals above, re-blended into five human-facing dimensions (see
    -- service.ReadinessScoreService#computeDimensions) plus the biggest
    -- strength/risk and the system's next recommended action, so the score
    -- is explainable rather than just a number.
    technical_dimension      DECIMAL(5,2) NOT NULL DEFAULT 0,
    problem_solving_dimension DECIMAL(5,2) NOT NULL DEFAULT 0,
    communication_dimension  DECIMAL(5,2) NOT NULL DEFAULT 0,
    behavioral_dimension     DECIMAL(5,2) NOT NULL DEFAULT 0,
    role_alignment_dimension DECIMAL(5,2) NOT NULL DEFAULT 0,
    biggest_strength         VARCHAR(255) DEFAULT NULL,
    biggest_risk             VARCHAR(255) DEFAULT NULL,
    next_recommended_action  VARCHAR(500) DEFAULT NULL,
    computed_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_readiness_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_readiness_user_time (user_id, computed_at)
) ENGINE=InnoDB;

-- ===========================================================================
-- final release MILESTONE ADDITIONS (Final 20%)
-- Personalised Learning Roadmap, Role & Company Preparation, Gamification,
-- and the Admin Module are included directly in this complete schema;
-- this file is the full from-scratch schema for a fresh install.
-- ===========================================================================

-- ---------------------------------------------------------------------
-- GAMIFICATION: points balance + streak per user
-- ---------------------------------------------------------------------
CREATE TABLE user_points (
    user_id             BIGINT PRIMARY KEY,
    total_points        INT NOT NULL DEFAULT 0,
    current_streak_days INT NOT NULL DEFAULT 0,
    longest_streak_days INT NOT NULL DEFAULT 0,
    last_activity_date  DATE DEFAULT NULL,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_points_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Full audit trail of every point-earning event (drives "recent activity"
-- on the gamification screen and makes the running total reproducible).
CREATE TABLE points_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    points      INT NOT NULL,
    reason      VARCHAR(150) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pointslog_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_pointslog_user_time (user_id, created_at)
) ENGINE=InnoDB;

-- Reference catalog of every badge the system can award (admin-manageable).
CREATE TABLE badge_catalog (
    badge_code  VARCHAR(50) PRIMARY KEY,
    badge_name  VARCHAR(100) NOT NULL,
    description VARCHAR(255) NOT NULL,
    criteria    VARCHAR(255) NOT NULL
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- PERSONALISED LEARNING ROADMAP
-- ---------------------------------------------------------------------
CREATE TABLE learning_roadmaps (
    roadmap_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    readiness_score DECIMAL(5,2) DEFAULT NULL,
    readiness_band  VARCHAR(50) DEFAULT NULL,
    summary         TEXT NOT NULL,
    generated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_roadmap_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_roadmap_user_time (user_id, generated_at)
) ENGINE=InnoDB;

-- Each concrete, actionable recommendation inside one generated roadmap
-- (e.g. "Close skill gap: Docker", "Practice weak topic: Operating
-- Systems", "Resolve 4 repeated mistakes in Core Java").
CREATE TABLE roadmap_items (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    roadmap_id        BIGINT NOT NULL,
    item_order        INT NOT NULL,
    category          ENUM('SKILL_GAP','WEAK_TOPIC','MISTAKE_REVIEW','MOCK_PRACTICE','ROLE_READINESS','GENERAL')
                         NOT NULL DEFAULT 'GENERAL',
    priority          ENUM('HIGH','MEDIUM','LOW') NOT NULL DEFAULT 'MEDIUM',
    title             VARCHAR(200) NOT NULL,
    description       VARCHAR(500) NOT NULL,
    related_topic_id  INT DEFAULT NULL,
    status            ENUM('PENDING','COMPLETED') NOT NULL DEFAULT 'PENDING',
    completed_at      TIMESTAMP NULL DEFAULT NULL,
    CONSTRAINT fk_ritem_roadmap FOREIGN KEY (roadmap_id) REFERENCES learning_roadmaps(roadmap_id) ON DELETE CASCADE,
    CONSTRAINT fk_ritem_topic FOREIGN KEY (related_topic_id) REFERENCES topics(topic_id) ON DELETE SET NULL,
    INDEX idx_ritem_roadmap (roadmap_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- ROLE & COMPANY PREPARATION
-- ---------------------------------------------------------------------
-- Admin-managed reference info about a target company (interview process,
-- focus areas, notes) shown alongside role/company-specific prep questions.
CREATE TABLE company_profiles (
    company_id         INT AUTO_INCREMENT PRIMARY KEY,
    company_name       VARCHAR(120) NOT NULL UNIQUE,
    industry           VARCHAR(100) DEFAULT NULL,
    interview_process  TEXT DEFAULT NULL,
    notes              TEXT DEFAULT NULL,
    is_active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Admin-managed bank of role-specific and/or company-specific interview
-- questions (technical, behavioural, system design). company_name is
-- nullable: NULL means "generic for this role, any company".
CREATE TABLE role_prep_questions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name       VARCHAR(120) NOT NULL,
    company_name    VARCHAR(120) DEFAULT NULL,
    question_text   TEXT NOT NULL,
    category        VARCHAR(80) NOT NULL DEFAULT 'Technical',
    difficulty      ENUM('EASY','MEDIUM','HARD') NOT NULL DEFAULT 'MEDIUM',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_rpq_role (role_name),
    INDEX idx_rpq_company (company_name)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- CAREER INTELLIGENCE INTEGRATION LAYER
-- ---------------------------------------------------------------------
-- Lightweight, purely-additive audit trail written by
-- service.CareerIntelligenceService#refreshAfterActivity every time an
-- assessment, a mistake-retry, or a mock interview finishes and the
-- system automatically recomputes the Interview Readiness Score and
-- regenerates the Learning Roadmap off the back of it. Never read by any
-- scoring/recommendation logic itself - it only makes the "Roadmap
-- Update" step of the Career Intelligence pipeline observable/auditable.
CREATE TABLE career_refresh_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    trigger_source  VARCHAR(40) NOT NULL,
    readiness_score DECIMAL(5,2) DEFAULT NULL,
    roadmap_id      BIGINT DEFAULT NULL,
    -- Adaptive Career Intelligence addition: a human-readable explanation of
    -- what changed and why (readiness delta, dimension deltas, new/changed
    -- roadmap priorities), generated from the user's actual before/after
    -- data by service.CareerIntelligenceService#refreshAfterActivityDetailed.
    details         TEXT DEFAULT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refreshlog_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_refreshlog_roadmap FOREIGN KEY (roadmap_id) REFERENCES learning_roadmaps(roadmap_id) ON DELETE SET NULL,
    INDEX idx_refreshlog_user_time (user_id, created_at)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- JOB DESCRIPTION ANALYSIS + RESUME/JD MATCHING (final release addition)
-- ---------------------------------------------------------------------
-- One job description a user pasted in for AI analysis, plus the
-- structured fields ai.AIService#analyzeJobDescription extracted from its
-- raw text. List-valued fields are stored "|"-delimited (dao.JobDescriptionDAO),
-- consistent with how user_profiles.resume_extracted_skills already stores
-- a flat skill list elsewhere in this schema.
CREATE TABLE job_descriptions (
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

-- An explainable Resume + JD match result (service.ResumeService#matchResumeToJobDescription):
-- the overall match score plus which required/preferred skills were a
-- Strong Match, Partial Match, Weak Evidence, or Missing, and which gaps
-- are highest priority. Persisted as an audit snapshot the same way
-- readiness_score_history and career_refresh_log already are.
CREATE TABLE job_match_results (
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
