# InterviewMentor

## Project Description

**InterviewMentor – Interview Preparation and Assessment System** is a Java-based application designed to help users prepare for technical and HR interviews through assessments, mock interviews, resume analysis, job-description analysis, skill-gap identification, AI-assisted answer evaluation, readiness scoring, and personalized learning recommendations.

## Features

- Resume analysis and skill extraction
- Job-description analysis and job matching
- Skill-gap identification
- Adaptive assessments
- AI-assisted descriptive-answer evaluation
- Local fallback evaluation when live AI is unavailable
- AI mock interviews with follow-up questions
- Interview readiness scoring
- Explainable recommendations
- Personalized learning roadmap
- Progress analytics
- Mistake analysis and retry
- Gamification with points, badges, and streaks
- User authentication and admin controls
- MySQL database persistence

## Technology Stack

- **Java 21**
- **Maven 3.9+**
- **MySQL 8.x**
- **JDBC / MySQL Connector/J**
- **JUnit 5**
- **OpenAI API** (optional)
- **org.json**

## Project Structure

```text
InterviewMentor/
├── database/
│   ├── schema.sql
│   ├── migration_v2_job_intelligence.sql
│   └── seed_data.sql
├── src/
│   ├── main/
│   │   └── java/
│   └── test/
│       └── java/
├── .env.example
├── .gitignore
├── pom.xml
├── README.md
├── run-demo.bat
└── run-interviewmentor.bat
```

## Requirements

Before running the project, install:

- JDK 21 or later
- Maven 3.9 or later
- MySQL 8.x
- OpenAI API key (optional, only for live AI features)

Check the installations:

```bash
java -version
mvn -version
mysql --version
```

## Installation & Setup

1. Clone or download the repository.

```bash
git clone <your-github-repository-url>
cd InterviewMentor
```

2. Configure MySQL using the database setup instructions below.

3. Copy `.env.example` to `.env` and enter your own configuration values.

4. Build the project:

```bash
mvn clean package
```

## Database Setup

Create the database:

```sql
CREATE DATABASE career_intelligence_db;
```

Import the schema:

```bash
mysql -u root -p career_intelligence_db < database/schema.sql
```

Apply the job-intelligence migration:

```bash
mysql -u root -p career_intelligence_db < database/migration_v2_job_intelligence.sql
```

Optional sample data:

```bash
mysql -u root -p career_intelligence_db < database/seed_data.sql
```

## Configuration

Use `.env.example` as the configuration template.

Example:

```text
DB_HOST=localhost
DB_PORT=3306
DB_NAME=career_intelligence_db
DB_USER=root
DB_PASSWORD=your_password

AI_API_KEY=your_api_key
AI_API_BASE_URL=https://api.openai.com/v1/responses
AI_MODEL=your_model
```

**Do not commit `.env`, database passwords, API keys, or other secrets to GitHub.**

## How to Run

Build the project:

```bash
mvn clean package
```

Run the application:

```bash
java -jar target/interviewmentor.jar
```

You can also use the provided Windows batch files:

```text
run-demo.bat
run-interviewmentor.bat
```

For the Maven exec plugin:

```bash
mvn exec:java
```

## Testing

Run the complete test suite:

```bash
mvn clean test
```

Latest verified test result:

```text
Tests run: 227
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

## System Architecture

The project follows a layered architecture:

```text
User / Console UI
        ↓
Service Layer
        ↓
AI / Business Logic
        ↓
DAO / JDBC Layer
        ↓
MySQL Database
```

- **UI Layer** – handles user interaction.
- **Service Layer** – manages application logic.
- **AI Layer** – handles AI evaluation and fallback processing.
- **DAO Layer** – performs database operations using JDBC.
- **Model Layer** – represents application data.
- **Utility Layer** – provides validation, security, parsing, and supporting functions.

## Security

The application includes:

- Password hashing
- Password salts
- Prepared statements
- Input validation
- Role-based access control
- Failed-login lockout handling
- Environment-based configuration

Never commit actual passwords, API keys, or other secrets.

## Limitations

- The application is primarily Java and console based.
- Live AI features depend on external API availability when enabled.
- Local AI evaluation is a fallback and may be less detailed than live AI evaluation.
- MySQL is required for persistent database functionality.

## Future Enhancements

Possible future improvements include:

- Enhanced web-based user interface
- More advanced AI interview analysis
- Additional assessment categories and questions
- Improved analytics and visualization
- Expanded job-market and career recommendations
- Cloud deployment and centralized access

## Team

**Project:** InterviewMentor – Interview Preparation and Assessment System

**Team Members:**
- Jeya Keerthana D
- Jeya Kiruthika D

**Project Guide:**
- Mrs. M. Geetha, Assistant Professor

**Institution:**
Chennai Institute of Technology
