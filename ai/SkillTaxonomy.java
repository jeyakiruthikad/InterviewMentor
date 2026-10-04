package com.careerintelligence.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Small, self-contained technical-skill reference taxonomy used by the
 * Resume AI Analysis, Resume-Based Questions, AI Mock Interview and
 * Interview Readiness Score features. Deliberately built as plain Java
 * data (no external NLP/ML dependency, consistent with the rest of the
 * `ai` package) so skill extraction works fully offline via
 * {@link AIServiceImpl}'s local heuristic path, with the live LLM path
 * still available as a smarter alternative when AI_API_KEY is configured.
 *
 * Each entry optionally names a `topics.topic_name` value it maps onto
 * (see database/schema.sql) so skills naturally present in the existing
 * current implementation question bank (Core Java, SQL, DS&A, OS, Networks, OOD)
 * can drive resume-based question selection straight from the DB, while
 * skills outside the question bank (React, Docker, AWS, ...) fall back to
 * AI-generated questions (see AIServiceImpl#generateQuestionsForSkills).
 */
public final class SkillTaxonomy {

    /** One taxonomy entry: canonical display name, category, an optional matching topic name, and a short reference blurb used only internally as the AI evaluator's "model answer" for AI-generated (non-question-bank) skill questions - never shown to the candidate. */
    public record SkillDef(String canonicalName, String category, String topicNameHint, String blurb, List<String> aliases) {
    }

    private static final List<SkillDef> SKILLS = List.of(
            // ---------------- Programming Languages ----------------
            new SkillDef("Java", "Programming Languages", "Core Java",
                    "Java is a class-based, object-oriented, platform-independent language that runs on the JVM, "
                            + "with automatic garbage collection, strong typing, and a large standard library.",
                    List.of()),
            new SkillDef("Python", "Programming Languages", null,
                    "Python is a high-level, dynamically-typed, interpreted language known for readable syntax, "
                            + "extensive libraries, and wide use in scripting, data science and web backends.",
                    List.of()),
            new SkillDef("C++", "Programming Languages", null,
                    "C++ is a compiled, statically-typed systems language offering manual memory management, "
                            + "object-oriented and generic programming, and high runtime performance.",
                    List.of("cpp")),
            new SkillDef("C", "Programming Languages", null,
                    "C is a low-level, procedural, compiled language with manual memory management, widely used "
                            + "for systems programming, embedded software and operating systems.",
                    List.of()),
            new SkillDef("JavaScript", "Programming Languages", null,
                    "JavaScript is a dynamically-typed scripting language that runs in browsers and on servers "
                            + "(Node.js), used for interactive front-end UIs and asynchronous, event-driven backends.",
                    List.of("js")),
            new SkillDef("TypeScript", "Programming Languages", null,
                    "TypeScript is a statically-typed superset of JavaScript that compiles to plain JavaScript, "
                            + "adding interfaces, generics and compile-time type checking for larger codebases.",
                    List.of("ts")),
            new SkillDef("C#", "Programming Languages", null,
                    "C# is a statically-typed, object-oriented language on the .NET platform, used for desktop, "
                            + "web (ASP.NET) and game (Unity) development with garbage collection.",
                    List.of("csharp", "c sharp")),
            new SkillDef("Go", "Programming Languages", null,
                    "Go (Golang) is a statically-typed, compiled language designed for simplicity and concurrency, "
                            + "with goroutines and channels for lightweight parallel programming.",
                    List.of("golang")),
            new SkillDef("Ruby", "Programming Languages", null,
                    "Ruby is a dynamic, object-oriented scripting language known for developer-friendly syntax and "
                            + "the Ruby on Rails web framework built around convention over configuration.",
                    List.of()),
            new SkillDef("PHP", "Programming Languages", null,
                    "PHP is a server-side scripting language designed for web development, widely used to build "
                            + "dynamic websites and content-management systems.",
                    List.of()),
            new SkillDef("Kotlin", "Programming Languages", null,
                    "Kotlin is a statically-typed language interoperable with Java on the JVM, offering null "
                            + "safety, concise syntax, and first-class support for Android development.",
                    List.of()),
            new SkillDef("SQL", "Programming Languages", "Database & SQL",
                    "SQL (Structured Query Language) is used to query, insert, update and manage data in "
                            + "relational databases via statements like SELECT, JOIN, GROUP BY and transactions.",
                    List.of()),

            // ---------------- Frameworks & Libraries ----------------
            new SkillDef("Spring Boot", "Frameworks & Libraries", null,
                    "Spring Boot is an opinionated Java framework for building stand-alone, production-ready "
                            + "Spring applications quickly, with auto-configuration and an embedded server.",
                    List.of("springboot", "spring-boot")),
            new SkillDef("Spring", "Frameworks & Libraries", null,
                    "Spring is a Java application framework providing dependency injection (IoC), aspect-oriented "
                            + "programming, and modules for web (MVC), data access and security.",
                    List.of()),
            new SkillDef("Hibernate", "Frameworks & Libraries", null,
                    "Hibernate is a Java Object-Relational Mapping (ORM) framework that maps Java classes to "
                            + "database tables, managing persistence, caching and transactions via JPA.",
                    List.of()),
            new SkillDef("React", "Frameworks & Libraries", null,
                    "React is a JavaScript library for building component-based user interfaces using a virtual "
                            + "DOM, one-way data flow, JSX, and hooks for state and lifecycle management.",
                    List.of("react.js", "reactjs")),
            new SkillDef("Angular", "Frameworks & Libraries", null,
                    "Angular is a TypeScript-based front-end framework providing components, dependency "
                            + "injection, two-way data binding, and a full toolchain for single-page applications.",
                    List.of()),
            new SkillDef("Vue.js", "Frameworks & Libraries", null,
                    "Vue.js is a progressive JavaScript framework for building user interfaces with reactive "
                            + "data binding, a component system, and a gentle learning curve.",
                    List.of("vue", "vuejs")),
            new SkillDef("Node.js", "Frameworks & Libraries", null,
                    "Node.js is a JavaScript runtime built on Chrome's V8 engine that lets JavaScript run "
                            + "server-side, using an event-driven, non-blocking I/O model well suited to APIs.",
                    List.of("node", "nodejs")),
            new SkillDef("Express.js", "Frameworks & Libraries", null,
                    "Express.js is a minimal, flexible Node.js web framework providing routing and middleware "
                            + "for building REST APIs and web servers.",
                    List.of("express", "expressjs")),
            new SkillDef("Django", "Frameworks & Libraries", null,
                    "Django is a high-level Python web framework that encourages rapid development with a "
                            + "built-in ORM, admin panel, and \"batteries-included\" philosophy.",
                    List.of()),
            new SkillDef("Flask", "Frameworks & Libraries", null,
                    "Flask is a lightweight Python micro web framework giving developers flexibility to add "
                            + "only the components (routing, templating, ORM) they need.",
                    List.of()),
            new SkillDef(".NET", "Frameworks & Libraries", null,
                    ".NET is Microsoft's cross-platform development platform for building web, desktop, cloud and "
                            + "mobile applications, primarily using C# or F#.",
                    List.of("dotnet", "asp.net", "asp net")),
            new SkillDef("JUnit", "Frameworks & Libraries", null,
                    "JUnit is a Java unit-testing framework providing annotations and assertions to write and "
                            + "run repeatable automated tests for individual units of code.",
                    List.of()),

            // ---------------- Databases ----------------
            new SkillDef("DBMS", "Databases", "Database & SQL",
                    "A DBMS (Database Management System) stores, organises and retrieves data, handling schema "
                            + "design, indexing, transactions (ACID), concurrency control and normalization.",
                    List.of("database management system")),
            new SkillDef("MySQL", "Databases", "Database & SQL",
                    "MySQL is a popular open-source relational database management system using SQL, widely "
                            + "used for web application backends with support for transactions and replication.",
                    List.of()),
            new SkillDef("PostgreSQL", "Databases", "Database & SQL",
                    "PostgreSQL is an advanced open-source relational database known for standards compliance, "
                            + "extensibility, and strong support for complex queries and JSON data.",
                    List.of("postgres")),
            new SkillDef("MongoDB", "Databases", null,
                    "MongoDB is a document-oriented NoSQL database that stores data as flexible, JSON-like BSON "
                            + "documents, favouring horizontal scaling and schema flexibility over strict schemas.",
                    List.of()),
            new SkillDef("Oracle", "Databases", "Database & SQL",
                    "Oracle Database is an enterprise relational database management system known for "
                            + "scalability, PL/SQL procedural extensions, and strong transactional guarantees.",
                    List.of()),
            new SkillDef("Redis", "Databases", null,
                    "Redis is an in-memory key-value data store used for caching, session storage and pub/sub "
                            + "messaging, valued for very low-latency reads and writes.",
                    List.of()),

            // ---------------- Cloud & DevOps ----------------
            new SkillDef("AWS", "Cloud & DevOps", null,
                    "AWS (Amazon Web Services) is a cloud platform offering compute (EC2), storage (S3), "
                            + "databases, and managed services for deploying and scaling applications.",
                    List.of("amazon web services")),
            new SkillDef("Azure", "Cloud & DevOps", null,
                    "Microsoft Azure is a cloud computing platform providing compute, storage, database and AI "
                            + "services for building, deploying and managing applications.",
                    List.of()),
            new SkillDef("GCP", "Cloud & DevOps", null,
                    "Google Cloud Platform (GCP) offers cloud compute, storage, data analytics and machine "
                            + "learning services for building and scaling applications.",
                    List.of("google cloud")),
            new SkillDef("Docker", "Cloud & DevOps", null,
                    "Docker packages an application with its dependencies into a lightweight, portable container "
                            + "image, ensuring consistent behaviour across development and production.",
                    List.of()),
            new SkillDef("Kubernetes", "Cloud & DevOps", null,
                    "Kubernetes is a container-orchestration platform that automates deployment, scaling and "
                            + "management of containerised applications across a cluster of machines.",
                    List.of("k8s")),
            new SkillDef("Jenkins", "Cloud & DevOps", null,
                    "Jenkins is an open-source automation server used to build CI/CD pipelines that compile, "
                            + "test and deploy code automatically on each change.",
                    List.of()),
            new SkillDef("CI/CD", "Cloud & DevOps", null,
                    "CI/CD (Continuous Integration / Continuous Delivery) is the practice of automatically "
                            + "building, testing and deploying code changes frequently and reliably.",
                    List.of("cicd", "continuous integration", "continuous delivery")),
            new SkillDef("Git", "Cloud & DevOps", null,
                    "Git is a distributed version-control system for tracking source-code changes, enabling "
                            + "branching, merging and collaborative development.",
                    List.of()),
            new SkillDef("Linux", "Cloud & DevOps", null,
                    "Linux is an open-source, Unix-like operating system widely used for servers, offering a "
                            + "shell/CLI, process and file-permission management, and package managers.",
                    List.of()),
            new SkillDef("Terraform", "Cloud & DevOps", null,
                    "Terraform is an infrastructure-as-code tool that lets teams define and provision cloud "
                            + "infrastructure declaratively using configuration files.",
                    List.of()),
            new SkillDef("Maven", "Cloud & DevOps", null,
                    "Maven is a Java build-automation and dependency-management tool driven by a declarative "
                            + "pom.xml file describing a project's dependencies, plugins and build lifecycle.",
                    List.of()),
            new SkillDef("Gradle", "Cloud & DevOps", null,
                    "Gradle is a build-automation tool using a Groovy/Kotlin DSL, popular for Java and Android "
                            + "projects for its flexible, incremental build performance.",
                    List.of()),

            // ---------------- CS Fundamentals ----------------
            new SkillDef("Data Structures", "CS Fundamentals", "Data Structures & Algorithms",
                    "Data structures (arrays, linked lists, stacks, queues, trees, graphs, hash maps) organise "
                            + "data to support efficient access, insertion, deletion and traversal.",
                    List.of()),
            new SkillDef("Algorithms", "CS Fundamentals", "Data Structures & Algorithms",
                    "Algorithms are step-by-step procedures (sorting, searching, recursion, dynamic programming, "
                            + "graph traversal) analysed for time and space complexity (Big-O).",
                    List.of()),
            new SkillDef("Operating Systems", "CS Fundamentals", "Operating Systems",
                    "Operating systems manage processes/threads, memory, scheduling, file systems and "
                            + "synchronization primitives (locks, semaphores) between concurrently running programs.",
                    List.of("os")),
            new SkillDef("Computer Networks", "CS Fundamentals", "Computer Networks",
                    "Computer networking covers the OSI/TCP-IP model, IP addressing, HTTP, DNS, routing and "
                            + "reliable data transfer between systems over a network.",
                    List.of("networking", "networks")),
            new SkillDef("OOP", "CS Fundamentals", "Object-Oriented Design",
                    "Object-oriented programming organises code around objects combining state and behaviour, "
                            + "using encapsulation, inheritance, polymorphism and abstraction.",
                    List.of("object-oriented programming", "object oriented programming")),
            new SkillDef("Design Patterns", "CS Fundamentals", "Object-Oriented Design",
                    "Design patterns (Singleton, Factory, Observer, Strategy, etc.) are reusable, proven "
                            + "solutions to common object-oriented software design problems.",
                    List.of()),
            new SkillDef("Multithreading", "CS Fundamentals", "Operating Systems",
                    "Multithreading runs multiple threads concurrently within a process, requiring "
                            + "synchronization to safely share memory and avoid race conditions and deadlocks.",
                    List.of("multi-threading", "concurrency")),
            new SkillDef("System Design", "CS Fundamentals", null,
                    "System design is the process of architecting scalable, reliable software systems, covering "
                            + "load balancing, caching, database sharding, and distributed-system trade-offs.",
                    List.of()),
            new SkillDef("REST API", "CS Fundamentals", null,
                    "A REST API exposes resources over HTTP using standard verbs (GET/POST/PUT/DELETE), "
                            + "statelessness, and resource-oriented URLs, typically exchanging JSON payloads.",
                    List.of("rest apis", "restful", "rest")),
            new SkillDef("Microservices", "CS Fundamentals", null,
                    "Microservices architecture splits an application into small, independently deployable "
                            + "services that communicate over the network, each owning its own data.",
                    List.of())
    );

    private SkillTaxonomy() {
    }

    public static List<SkillDef> all() {
        return SKILLS;
    }

    public static java.util.Optional<SkillDef> byCanonicalName(String canonicalName) {
        return SKILLS.stream().filter(s -> s.canonicalName().equalsIgnoreCase(canonicalName)).findFirst();
    }

    /**
     * Scans free text for every taxonomy skill (canonical name or any alias),
     * matching whole words/tokens case-insensitively. Symbol-bearing terms
     * (C++, C#, .NET) use a punctuation-aware boundary check instead of the
     * regex {@code \b} word boundary, which does not behave usefully around
     * '+', '#' or '.'.
     */
    public static List<SkillDef> findMentioned(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<SkillDef> found = new java.util.ArrayList<>();
        for (SkillDef skill : SKILLS) {
            if (mentions(text, skill.canonicalName()) || skill.aliases().stream().anyMatch(a -> mentions(text, a))) {
                found.add(skill);
            }
        }
        return found;
    }

    /** Skill terms short/common enough to collide with ordinary English words (e.g. the "Go" language vs. "go to the gym"); these require an exact-case match instead of case-insensitive matching to cut down false positives. */
    private static final java.util.Set<String> CASE_SENSITIVE_TERMS = java.util.Set.of("go", "r", "c");

    private static boolean mentions(String text, String term) {
        String quoted = Pattern.quote(term);
        boolean symbolBearing = term.chars().anyMatch(c -> c == '+' || c == '#' || c == '.');
        boolean caseSensitive = CASE_SENSITIVE_TERMS.contains(term.toLowerCase(java.util.Locale.ROOT));
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
        Pattern p = symbolBearing
                ? Pattern.compile("(?<![A-Za-z0-9])" + quoted + "(?![A-Za-z0-9])", flags)
                : Pattern.compile("\\b" + quoted + "\\b", flags);
        return p.matcher(text).find();
    }

    /** Groups a flat skill list into an ordered category -> skill-name-list map, for display. */
    public static Map<String, List<String>> groupByCategory(List<String> skillNames) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (String name : skillNames) {
            String category = byCanonicalName(name).map(SkillDef::category).orElse("Other");
            grouped.computeIfAbsent(category, k -> new java.util.ArrayList<>()).add(name);
        }
        return grouped;
    }
}
