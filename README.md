# InterviewMentor

A Java 21 console application that combines **resume intelligence, job-description matching, adaptive assessments, AI answer evaluation, mock interviews, explainable recommendations, learning roadmaps, progress analytics, gamification and secure administration** into one integrated platform.

The system is built around a single closed loop:

```
Resume → JD → Job Match → Skill Gaps → Roadmap
       → Assessment → AI Evaluation → Mock Interview → Improved Readiness
                                                              ↓
                                            (feeds back into the roadmap)
```

Every score the product shows is derived from something the candidate actually did, and every recommendation can explain itself.

---

## Quick start (60 seconds, no database needed)

### Windows: easiest way

If you are using Windows, you can double-click **`run-demo.bat`**. It builds the project if needed and launches the guided demo. No MySQL, login, or API key is required.

For the full application, double-click **`run-interviewmentor.bat`**. On the first run it creates `.env` from `.env.example` and opens it so you can enter your MySQL settings. After that, the same file builds and starts the application.

The application also gives you a guided-demo fallback if MySQL is unavailable: when the normal startup cannot connect to the database, press **Enter** at the `Run the guided demo instead? [Y/n]` prompt.

### Command line

```bash
mvn clean package
java -jar target/interviewmentor.jar --demo
```

This runs the **guided demo**: a nine-stage walkthrough of the entire product using a realistic candidate, with **no MySQL, no API key and no login required**. It is the fastest way to see what InterviewMentor does.

To run the real application (requires MySQL — see [Database setup](#database-setup)):

```bash
java -jar target/interviewmentor.jar
```

### Command-line flags

| Flag | Effect |
|---|---|
| `--demo` | Runs the guided nine-stage demo. No database or API key required. |
| `--auto` | With `--demo`, runs without pausing between stages (for piping or recording). |
| `--color` / `--no-color` | Forces terminal colour on or off. Colour is auto-detected otherwise. |

Colour is also disabled automatically when `NO_COLOR` is set, when `TERM=dumb`, or when output is piped.

---

## Feature overview

### Real interview experience

The primary practice flow is designed to feel like an interview rather than a configuration screen. When you start an **AI Mock Interview**, InterviewMentor automatically uses the target role and company from your profile, your resume skills, job-description requirements, weak topics and previous mistakes to prepare the session. You do not choose the role, company, topics, number of questions or duration each time.

During the interview:

- The interviewer opens the session and asks questions one at a time.
- You answer naturally in the console; technical details such as database IDs and internal scoring rules stay out of the live conversation.
- Strong or weak answers can trigger a single contextual follow-up, so the conversation adapts instead of behaving like a fixed questionnaire.
- Per-answer scoring is kept out of the live interview so the flow is not interrupted.
- When the interview ends, you receive the full AI report: overall performance, strengths, weaknesses, key mistakes, readiness movement and the next recommended action.

Adaptive assessments use the same principle: the application automatically selects useful topics, question count and time, while the live screen focuses on the questions and remaining time rather than configuration.

### InterviewMentor Dashboard

A single consolidated screen rendering:

- **Interview Readiness** — overall score, band, and a sparkline trend across all historical snapshots
- **Job Match** — score plus the weighted breakdown that produced it, and a skill-by-skill strong / partial / missing split
- **Strengths and skill gaps** — three distinct gap types: *missing from resume*, *claimed but weak*, and *measured below competency*
- **Topic mastery** — per-topic accuracy bars with attempt counts and improving/steady/declining momentum
- **Assessment and mock interview scores** — averages, best, accuracy, and mistake-resolution progress
- **Roadmap progress** — completion bar plus the top outstanding priorities
- **InterviewMentor Updates** — the "what changed and why" feed written after every activity
- **Recommended Next Action** — with full explainability (below)

Each panel has a purpose-written empty state, so a new account is told what to do rather than shown a blank heading.

### Interview Readiness Score — five explainable dimensions

The headline score is a weighted blend of five human-facing dimensions, each with its own evidence note:

| Dimension | Weight | Driven by |
|---|---|---|
| Technical | 30% | Accuracy on language/framework/database questions |
| Problem Solving | 25% | Data structures, algorithms and system-design accuracy |
| Communication | 20% | Descriptive-answer and mock-interview communication scores |
| **Behavioral** | 15% | **Real behavioral-interview performance** — see below |
| Role Alignment | 10% | Resume-to-target-role skill coverage + latest Job Match score |

**Behavioral is grounded in real STAR performance, not app-usage proxies.** It scans the candidate's own AI Mock Interview answers for behavioral/situational questions ("tell me about a time you…") and blends each one's **STAR (Situation/Task/Action/Result) completeness** with its **AI-graded score** — both the local heuristic evaluator and the live-LLM path record this per answer. Only *before* a candidate has answered a single real behavioral question does the dimension fall back to a clearly-labelled placeholder built from mistake-resolution discipline and practice streak, and the Recommended Next Action says so explicitly ("answer a behavioral question … your Behavioral score is still a … placeholder").

### Job Match — six weighted signals

Job Match blends every available evidence type for a target role, not just a keyword skill list:

| Signal | Weight | What it checks |
|---|---|---|
| Skill coverage | 40% | Required/preferred JD skills present on the resume |
| Resume evidence strength | 10% | How concretely each skill is backed (project/experience text vs. a bare list) |
| Relevant experience | 15% | Years of experience against the JD's implied seniority bar |
| Project relevance | 15% | JD technologies that show up in the resume's project descriptions |
| Assessment performance | 10% | Measured accuracy on the JD's required topics |
| Interview performance | 10% | Mock-interview scores on the same topics |

Because it pulls from resume text, assessments *and* interviews, the score reflects what a candidate can demonstrate, not just what they typed on a resume.

### Explainability

Every recommendation answers two questions explicitly:

**Why this recommendation?** — evidence lines, each tagged with the data source it came from:

```
• [Performance] Communication is your lowest readiness dimension at 31.0/100,
  so it is capping your overall score of 55.0.
• [Job Description] Your resume matches Senior Backend Engineer at 46.5%;
  Kubernetes, Kafka are required by the JD but absent from your resume.
• [Resume vs Performance] You list Docker on your resume but score below the
  pass mark on it in practice - an interviewer is likely to probe there.
• [Assessments] Your weakest measured topic is System Design at 38.0% accuracy
  over 8 attempt(s), below the 60% competency line.
• [Mistake Log] 9 of your 14 logged mistake(s) are still unresolved.
```

**What should I do next?** — one concrete, prioritised action that names a screen the user can open: unresolved mistakes first, then missing target-role skills, then the weakest dimension, then "take a mock interview" if none exists yet, then "answer a real behavioral question" if Behavioral is still on its placeholder, then a push toward the 80+ Interview Ready band, then "keep up the momentum."

Roadmap items are individually explainable too: each cites the specific gap that produced it (`RecommendationExplainer.explainRoadmapItem`).

Importantly, the explainer **never overrides** the readiness engine's own recommendation — it explains it. It only supplies an action of its own when the engine has not produced one yet (a brand-new account).

### Visual analytics

All charts are pure text primitives in `ui/Charts.java`, so the visual layer is unit-tested:

- Progress gauges with band colouring (`████████████░░░░░░░░ 51.4%`)
- Labelled bar charts for dimensions, topic mastery and score breakdowns
- Unicode sparklines for the readiness trend (`▁▂▃▄▅▆█`)
- Delta badges (`▲ +18.4`, `▼ -2.1`, `● 0.0`) coloured by whether the movement is good news
- Fraction bars for roadmap and mistake-resolution progress

### AI behaviour and failure handling

The application has two AI paths:

1. **Live OpenAI path** — used when `AI_API_KEY` is configured (with `AI_API_BASE_URL` optionally overridden).
2. **Local semantic engine** — deterministic skill extraction, concept-based answer evaluation, STAR/behavioral analysis, question generation and follow-ups.

The local path is a **fully supported mode, not a crash handler**: the entire product, including the Behavioral readiness signal, works with no API key at all. `ai/AIHealth.java` tracks every live call and every fallback, so:

- The dashboard header always states which engine is serving AI features.
- After a degraded call, the UI shows which operation fell back and why (`HTTP 503`, `connection timed out`, …).
- An LLM outage degrades one feature gracefully instead of breaking the session.

---

## Guided demo

```bash
java -jar target/interviewmentor.jar --demo
```

The demo follows **Priya Raman**, a mid-level backend developer targeting a Senior Backend Engineer role at a payments company, through nine stages:

| # | Stage | What it shows |
|---|---|---|
| 1 | Resume analysis | **Real `AIServiceImpl` call** — skills extracted and mapped to question-bank topics |
| 2 | Job description | Required/preferred skills parsed out of free text |
| 3 | Job Match | Explainable score with its six-signal weighted breakdown |
| 4 | Skill gaps | The three gap types, plus the topic-mastery map |
| 5 | Roadmap | Eight prioritised items, each explaining the gap that produced it |
| 6 | Assessment | Adaptive difficulty rising and falling per answer |
| 7 | AI evaluation | **Real `AIServiceImpl` call** — a descriptive answer scored live |
| 8 | Mock interview | Personalised questions, smart follow-ups, real STAR breakdown |
| 9 | Improved readiness | Full before/after comparison and the updated dashboard |

Two properties make the demo worth trusting:

- **It runs real production code.** Stages 1 and 7 call the genuine AI service (which works fully offline), and every dashboard is drawn by the same `DashboardRenderer` the live app uses, fed through the same `DashboardView.from(...)` factory. Only the candidate's data is supplied.
- **The adaptive change is explicit.** Stage 9 prints a before/after table:

```
  Metric                Before        After         Change
  ────────────────────────────────────────────────────────
  Readiness score       41.2          71.8          ▲ +30.6
  Band                  NOT READY     ALMOST READY  —
  Job match             46.5          61.5          ▲ +15.0
  Weak topics           3             0             ▼ -3.0
  Unresolved mistakes   9             2             ▼ -7.0
  Roadmap done          0/8           6/8           ▲ +6.0

  Readiness dimensions
  Technical           56.0  → 76.0  ▲ +20.0
  Problem Solving     48.0  → 70.0  ▲ +22.0
  Communication       30.0  → 68.0  ▲ +38.0
  Behavioral          32.0  → 71.0  ▲ +39.0
  Role Alignment      52.0  → 69.0  ▲ +17.0
```

The Communication and Behavioral jumps are the demo's key narrative beat: technical practice alone cannot move them — only the mock interview, with a real STAR-graded behavioral answer, can. This is asserted by a test, not just claimed.

---

## OpenAI live-AI notes

The live integration uses the OpenAI Responses API over HTTPS with the JDK built-in `HttpClient`; no extra OpenAI Java dependency is required. The API key is sent as a Bearer token and is never stored in source code.

If an OpenAI request fails because of authentication, connectivity, quota, rate limiting, or an invalid response, the application records the fallback event and continues with the local semantic engine.

## Architecture

```
┌────────────────────────────────────────────────────────┐
│  Console UI            ui/                             │
│  menus + Ansi, Charts, Layout, DashboardRenderer       │
├────────────────────────────────────────────────────────┤
│  View model            service/DashboardView           │
│  pure, DB-free, unit-tested derivations                │
├────────────────────────────────────────────────────────┤
│  Service layer         service/                        │
│  CareerIntelligenceService orchestrates;               │
│  RecommendationExplainer explains                      │
├────────────────────────────────────────────────────────┤
│  AI layer              ai/                             │
│  AIServiceImpl (live LLM + local engine), AIHealth     │
├────────────────────────────────────────────────────────┤
│  DAO layer             dao/   JDBC                     │
├────────────────────────────────────────────────────────┤
│  MySQL 8.x                                             │
└────────────────────────────────────────────────────────┘
```

**Key design decision — the presentation split.** The dashboard screen is only a controller. It loads a `DashboardView` and hands it to `DashboardRenderer`. The view-model performs every derivation (trend series, strengths/gaps split, mastery ordering, roadmap progress) as pure functions, and the renderer returns a `List<String>` rather than printing directly. That is what makes both the visual layer and the demo testable without a database or a terminal.

`CareerIntelligenceService` remains the orchestration hub. Feature services continue to own their own business logic; nothing was rewritten.

### Project structure

```text
InterviewMentor/
├── database/
│   ├── schema.sql                        # Complete schema for a fresh install
│   ├── migration_v2_job_intelligence.sql # JD/job-match tables
│   └── seed_data.sql                     # Optional demo users and content
├── src/
│   ├── main/java/com/careerintelligence/
│   │   ├── ai/         # LLM integration, skill taxonomy, STAR analysis, AIHealth
│   │   ├── dao/        # JDBC data access
│   │   ├── database/   # Connection management
│   │   ├── demo/       # DemoDataFactory + DemoFlow (guided demo)
│   │   ├── model/      # Domain models and enums
│   │   ├── service/    # Business logic, DashboardView, RecommendationExplainer
│   │   ├── ui/         # Menus, Ansi, Charts, Layout, DashboardRenderer
│   │   └── util/       # Validation, security, adaptive difficulty
│   └── test/java/com/careerintelligence/
│       ├── ai/         # AI evaluation, STAR analysis and generation tests
│       ├── dao/        # DAO/mapping tests
│       ├── demo/       # Demo integrity + AI health tests
│       ├── service/    # Service, view-model and explainability tests
│       ├── ui/         # Chart primitive tests
│       └── util/       # Utility and security tests
├── .env.example
├── run-demo.bat                         # Double-click to launch the offline demo
├── run-interviewmentor.bat              # Double-click to build/start the full app
├── pom.xml
└── README.md
```

---

## Requirements

For the full application:

1. **JDK 21 or later**
2. **Maven 3.9+**
3. **MySQL 8.x**
4. **An OpenAI API key** *(optional; the application has a deterministic local fallback)*

Check your installations:

```powershell
java -version
mvn -version
mysql --version
```

> **Windows note:** Run the Maven commands from the folder that contains `pom.xml`.

## Database setup

### Fresh installation (recommended)

The following commands are for Windows PowerShell/Command Prompt with MySQL 8.x installed and the `mysql` command available on `PATH`.

**1. Create the database**

```powershell
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS career_intelligence_db;"
```

**2. Load the complete schema**

Run from the project root — the folder containing `pom.xml`:

```powershell
mysql -u root -p career_intelligence_db < database/schema.sql
```

`schema.sql` is the complete from-scratch schema for the current release.

**3. Apply the compatibility migration**

```powershell
mysql -u root -p career_intelligence_db < database/migration_v2_job_intelligence.sql
```

The migration checks whether columns/tables already exist, so it is safe after a fresh schema load and is also intended for older databases.

**4. Load optional sample data**

```powershell
mysql -u root -p career_intelligence_db < database/seed_data.sql
```

`seed_data.sql` is optional and provides sample data for testing/demo purposes.

### Verify the database

```powershell
mysql -u root -p -e "USE career_intelligence_db; SHOW TABLES;"
```

You should see the core application tables plus `job_descriptions` and `job_match_results`.

### Existing database

For an older InterviewMentor database, do not recreate the database. Run:

```powershell
mysql -u root -p career_intelligence_db < database/migration_v2_job_intelligence.sql
```

Then verify with:

```sql
USE career_intelligence_db;
SHOW TABLES;
```

## Configuration

### 1. Create `.env`

Copy the supplied template:

```powershell
Copy-Item .env.example .env
```

Open `.env` and set your MySQL credentials:

```text
DB_HOST=localhost
DB_PORT=3306
DB_NAME=career_intelligence_db
DB_USER=root
DB_PASSWORD=your_mysql_password
```

### 2. Configure OpenAI

Create an OpenAI API key in your OpenAI account. Keep the real key only in your local `.env` file:

```text
AI_API_KEY=your_openai_api_key
AI_API_BASE_URL=https://api.openai.com/v1/responses
AI_MODEL=gpt-5.6-luna
```

The live integration uses the OpenAI Responses API for semantic answer evaluation, resume skill extraction, job-description analysis, question generation, and mock-interview follow-ups. The model can be changed through `AI_MODEL`.

**File uploads:**
- **Resume:** From **Resume & Skills → Upload & Analyze Resume**, a native file picker opens. Select your `.pdf`, `.docx`, or `.txt` resume; no file path needs to be typed.
- **Job description:** From **Resume & Skills → Analyze a Job Description & View Job Match Score**, choose **Upload a file** to select a `.pdf`, `.docx`, or `.txt` job description, or paste the text directly.

**Never commit `.env`, your OpenAI API key, or your MySQL password to GitHub or the submission ZIP.** Share `.env.example` only.

### User-friendly input

InterviewMentor is console-first, but input that is awkward to type is handled with simpler controls:

- **Resume:** native file picker for `.pdf`, `.docx`, and `.txt`.
- **Job description:** native file picker for `.pdf`, `.docx`, and `.txt`, or direct text paste.
- **Passwords:** hidden when the application is running in a real Windows terminal; IDE/test consoles fall back to normal input so they remain compatible.
- **Experience level:** choose from numbered options instead of typing enum values such as `FRESHER` or `SENIOR`.
- **Yes/no prompts:** accept `Y`, `N`, `yes`, `no`, or Enter for the displayed default.
- **Database failure:** startup offers the guided demo instead of simply exiting.

### 3. Optional AI threshold

```text
AI_PASS_THRESHOLD=60
```

This controls the minimum semantic-evaluation score used by the application.

## Build and run

From the project root — the folder containing `pom.xml`:

```powershell
mvn clean test
mvn clean package
```

The runnable JAR is created at:

```text
target\interviewmentor.jar
```

Run the full application:

```powershell
java -jar target\interviewmentor.jar
```

### Guided demo

The guided demo is useful for presentations and can run without MySQL:

```powershell
java -jar target\interviewmentor.jar --demo
```

### Run through Maven

```powershell
mvn exec:java
```

### If MySQL connection fails

1. Make sure the **MySQL80** Windows service is running.
2. Confirm `.env` is in the same folder as `pom.xml`.
3. Check `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, and `DB_PASSWORD`.
4. Verify the database:

```sql
SHOW DATABASES;
USE career_intelligence_db;
SHOW TABLES;
```

5. Verify the MySQL port:

```sql
SHOW VARIABLES LIKE 'port';
```

If MySQL is unavailable, use `--demo` to demonstrate the application without a database.

---


## Complete Windows run sequence

If you are setting up InterviewMentor from a fresh extraction, follow these steps in order.

### Step 1 — Install prerequisites

Install:

- JDK 21+
- Maven 3.9+
- MySQL 8.x

Verify:

```powershell
java -version
mvn -version
mysql --version
```

### Step 2 — Open the project folder

```powershell
cd path\to\InterviewMentor
```

You must be in the folder containing `pom.xml`.

### Step 3 — Create and populate MySQL

```powershell
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS career_intelligence_db;"
mysql -u root -p career_intelligence_db < database/schema.sql
mysql -u root -p career_intelligence_db < database/migration_v2_job_intelligence.sql
mysql -u root -p career_intelligence_db < database/seed_data.sql
```

### Step 4 — Create `.env`

```powershell
Copy-Item .env.example .env
```

Set your real MySQL password:

```text
DB_HOST=localhost
DB_PORT=3306
DB_NAME=career_intelligence_db
DB_USER=root
DB_PASSWORD=YOUR_MYSQL_PASSWORD
```

For live OpenAI features:

```text
AI_API_KEY=YOUR_OPENAI_API_KEY
AI_API_BASE_URL=https://api.openai.com/v1/responses
AI_MODEL=gpt-5.6-luna
```

The OpenAI key is optional. Without it, InterviewMentor uses its deterministic local AI engine.

**Never put a real API key or password in `.env.example`, GitHub, screenshots, or the submission ZIP.**

### Step 5 — Run the tests

```powershell
mvn clean test
```

The final release suite contains 209 tests and should report zero failures and zero errors.

### Step 6 — Build the runnable JAR

```powershell
mvn clean package
```

The JAR is created at:

```text
target\interviewmentor.jar
```

### Step 7 — Run the full application

```powershell
java -jar target\interviewmentor.jar
```

### Step 8 — Run the presentation demo

The guided demo does not require MySQL or an OpenAI API key:

```powershell
java -jar target\interviewmentor.jar --demo
```

For an uninterrupted demo:

```powershell
java -jar target\interviewmentor.jar --demo --auto
```

### Step 9 — If MySQL connection fails

Make sure the **MySQL80** Windows service is running.

Then verify:

```sql
SHOW VARIABLES LIKE 'port';
USE career_intelligence_db;
SHOW TABLES;
```

Also confirm that `.env` is in the same folder as `pom.xml` and that its database values match your MySQL installation.

### Step 10 — If live OpenAI calls fail

The application automatically falls back to the local semantic engine. Check:

```text
AI_API_KEY
AI_API_BASE_URL
AI_MODEL
```

Make sure the OpenAI API key is valid and has API access. Never paste the key into source code or commit it to the repository.

## Testing

```bash
mvn clean test
```

**209 tests across 21 test classes.** The suite covers:

| Area | Coverage |
|---|---|
| AI evaluation | Score breakdown, STAR analysis (both the local heuristic and live-LLM paths), malformed-LLM-response fallback |
| Personalised questions | Ordering by gap priority, difficulty adaptation |
| Smart follow-ups | Weak-answer probing vs. strong-answer escalation |
| Resume & role matching | Skill extraction, role match, JD match |
| Readiness / Next Best Action | Priority ordering across mistakes, missing skills, weakest dimension, no-mock-yet and no-real-behavioral-answer-yet states |
| Explainability | Every "why" line is grounded in real user data and tagged with its source |
| Dashboard view-model | Trend reversal, mastery thresholds, roadmap progress, empty states |
| Chart primitives | Gauge fill, clamping, sparkline scaling, flat-series divide-by-zero, truncation |
| Demo integrity | Scores improve monotonically, gaps close, resume/JD data is self-consistent |
| AI health | Degradation reporting, bounded event history, null-safety |
| Adaptive difficulty | Streak and trend classification |
| Security | Login lockout, admin authorization |

The demo tests are deliberately adversarial about data integrity — for example, they assert that every skill listed as "missing from the resume" genuinely does not appear on the demo resume, and that "claimed but weak" skills genuinely do. That prevents the demo from drifting into telling a story its own data contradicts.

## Security notes

- Passwords hashed with PBKDF2WithHmacSHA256 and per-user salts
- All database access via prepared statements
- Admin operations role-gated
- Failed logins trigger account lockout
- Credentials and AI keys loaded from environment configuration, never source
- User input validated before persistence

## Limitations

- **Console-first by design.** InterviewMentor remains a Java 21 console application, with a native desktop file picker used for resume and job-description uploads so users can select a PDF/DOCX/TXT file instead of typing a local path. The layered architecture (`ui/` → `service/` → `dao/` → MySQL) remains unchanged.
- **Live AI is optional, not required.** Every feature — including Job Match, resume parsing, question generation, evaluation, and Behavioral/STAR scoring — has a deterministic local fallback so the whole product is usable and demoable with no API key. Answer quality on nuanced, open-ended responses is naturally stronger with a live LLM configured.
- **Single relational schema, no multi-tenant isolation.** The schema assumes one shared MySQL instance; there's no per-organisation partitioning.
- **STAR/behavioral scoring is text-heuristic when offline.** The local engine detects Situation/Task/Action/Result structure from phrasing patterns; it's a good proxy but not a substitute for a human interviewer's judgement, and the live-LLM path is more nuanced when configured.

## What changed in this final polish pass

Additive and corrective, not a rewrite — no existing feature was removed.

- **Behavioral readiness now uses real behavioral-interview/STAR data.** `ReadinessScoreService` scans the user's own mock-interview answers for behavioral questions and blends their STAR completeness with their AI-graded score; mistake-resolution/streak now only serve as a clearly-labelled placeholder before the user has a real behavioral answer. `AIServiceImpl`'s local heuristic evaluator now persists the STAR score in the same feedback-text convention the live LLM path already used, so this works fully offline.
- **Rebranded to InterviewMentor** throughout the README, `pom.xml`, console banners, menus, the dashboard, the guided demo, and configuration templates.
- **Repository cleanup**: removed generated/IDE artifacts and kept real credentials out of the submission.
- **OpenAI integration**: the live LLM path uses the OpenAI Responses API with Bearer authentication. The model is configurable through `AI_MODEL`, with `gpt-5.6-luna` supplied as the default.

## Realistic mock interview flow

The AI Mock Interview now behaves like a human interviewer (same OpenAI integration, same local fallback, no schema changes):

1. **Behavioral opening** - 2-3 questions (introduction, project experience, teamwork, challenges), lightly personalised to the target role and resume skills.
2. **Personalised technical questions** - from the topic bank, resume, skill gaps, past mistakes and current difficulty.
3. **Answer analysis** - the existing scoring/STAR evaluation decides the next move.
4. **Intelligent follow-up** - a weak technical answer gets a clarification aimed at the missing concept, a strong one gets a deeper question, and a behavioral answer missing Situation/Task/Action/Result is probed naturally for that element. Each reply reacts to something the candidate actually said.
5. **Natural pacing** - at most 2 follow-ups per technical topic, 1 per behavioral question and 4 per session; near-duplicate questions are never asked twice.
6. **Final evaluation** - unchanged scoring, readiness score and report.
