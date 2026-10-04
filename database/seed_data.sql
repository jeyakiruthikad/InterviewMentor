-- =====================================================================
-- InterviewMentor
-- Sample / seed data (MySQL 8.x)
-- =====================================================================
-- Run AFTER schema.sql:
--   mysql -u root -p career_intelligence_db < database/seed_data.sql
-- =====================================================================

USE career_intelligence_db;

-- ---------------------------------------------------------------------
-- SAMPLE USERS
-- Passwords below are hashed with PBKDF2WithHmacSHA256 (65536 iterations,
-- 256-bit key) exactly as com.careerintelligence.util.PasswordUtil does at
-- runtime - these are NOT placeholders, they are real, verified hashes.
--
--   Username          Password       Role
--   ----------  -------------  -----
--   demo_admin       Admin@123      ADMIN
--   demo_candidate    Password@123   USER
--   demo_candidate_2  Password@123   USER
-- ---------------------------------------------------------------------
INSERT INTO users (username, email, password_hash, password_salt, full_name, phone, role, is_active) VALUES
('demo_admin',      'demo.demo_admin@example.com',     'k7ZakGUZg5eWK9b8ZfBXNH/eQKYgJzINsEloJabDCRg=', '0NvCIWB2xDY9RR2b0S3X3Q==', 'System Administrator', '9999999999', 'ADMIN', TRUE),
('demo_candidate',   'john.doe@example.com',        'Kh3Hb6f5sJxbdty6dhzZjv/jZIuKeMYukC8WsreIrVg=', 'jYH1yn8m5X/n/Vwb2pltyQ==', 'Demo Candidate',             '9876543210', 'USER',  TRUE),
('demo_candidate_2', 'jane.smith@example.com',      'p81gMLWguuNfzZpf5lSlosSpnODEXyPvHLNzAz6vlGQ=', 'vEnvX/S051qr5uhYCBp4NA==', 'Demo Candidate 2',           '9876500000', 'USER',  TRUE);

INSERT INTO user_profiles (user_id, target_role, target_company, experience_level, bio) VALUES
((SELECT user_id FROM users WHERE username = 'demo_admin'),      NULL,                 NULL,      'SENIOR', 'System demo_administrator account.'),
((SELECT user_id FROM users WHERE username = 'demo_candidate'),   'Backend Developer',  'Google',  'FRESHER','Aspiring backend developer preparing for interviews.'),
((SELECT user_id FROM users WHERE username = 'demo_candidate_2'), 'Full Stack Developer','Amazon', 'JUNIOR', 'One year of experience, targeting product companies.');

-- ---------------------------------------------------------------------
-- TOPICS
-- ---------------------------------------------------------------------
INSERT INTO topics (topic_name, category, description) VALUES
('Core Java',            'Programming',     'Java language fundamentals, OOP, collections, exceptions'),
('Database & SQL',       'Databases',       'Relational databases, SQL queries, normalization, transactions'),
('Data Structures & Algorithms', 'CS Fundamentals', 'Arrays, linked lists, trees, sorting, complexity analysis'),
('Operating Systems',     'CS Fundamentals', 'Processes, threads, memory management, scheduling'),
('Computer Networks',     'CS Fundamentals', 'OSI model, TCP/IP, HTTP, DNS'),
('Object-Oriented Design','Programming',     'OOP principles, design patterns, SOLID');

-- ---------------------------------------------------------------------
-- CORE JAVA QUESTIONS (topic 1)
-- ---------------------------------------------------------------------
INSERT INTO questions (topic_id, question_text, question_type, difficulty, correct_answer, explanation, marks) VALUES
(1, 'Which keyword is used to inherit a class in Java?', 'MCQ', 'EASY', 'B', 'The "extends" keyword is used for class inheritance in Java.', 1),
(1, 'Java supports multiple inheritance of classes (not interfaces).', 'TRUE_FALSE', 'EASY', 'FALSE', 'Java does not support multiple inheritance of classes to avoid the diamond problem; it is supported via interfaces.', 1),
(1, 'Which of these is NOT a primitive data type in Java?', 'MCQ', 'EASY', 'C', 'String is a class (reference type) in Java, not a primitive type.', 1),
(1, 'What is the default value of a boolean instance variable in Java?', 'MCQ', 'EASY', 'A', 'Uninitialized boolean instance variables default to false.', 1),
(1, 'The "static" keyword means a member belongs to the class rather than any instance.', 'TRUE_FALSE', 'EASY', 'TRUE', 'Static members are shared across all instances and belong to the class itself.', 1),
(1, 'Which collection class allows duplicate elements and maintains insertion order?', 'MCQ', 'MEDIUM', 'B', 'ArrayList allows duplicates and preserves insertion order, unlike Set implementations.', 1),
(1, 'What does the "final" keyword do when applied to a variable?', 'MCQ', 'MEDIUM', 'A', 'A final variable can only be assigned once; it becomes a constant reference/value.', 1),
(1, 'In Java, an interface can have both abstract and default methods (Java 8+).', 'TRUE_FALSE', 'MEDIUM', 'TRUE', 'Since Java 8, interfaces can define default and static methods with bodies.', 1),
(1, 'Which exception is thrown when dividing an integer by zero in Java?', 'MCQ', 'MEDIUM', 'C', 'Integer division by zero throws ArithmeticException at runtime.', 1),
(1, 'What is the purpose of the "volatile" keyword in Java?', 'DESCRIPTIVE', 'HARD', 'The volatile keyword ensures visibility of changes to a variable across threads by preventing threads from caching its value locally, forcing reads/writes to main memory, without providing atomicity.', 'Tests understanding of Java memory model and concurrency.', 3),
(1, 'Explain the difference between "==" and ".equals()" when comparing objects in Java.', 'DESCRIPTIVE', 'HARD', '"==" compares object references (memory addresses) for objects, while ".equals()" compares logical/content equality as defined by the class; for primitives "==" compares values directly.', 'Tests understanding of reference vs value equality.', 3),
(1, 'Which of the following correctly creates a thread by implementing Runnable?', 'MCQ', 'MEDIUM', 'A', 'Implementing Runnable and passing the instance to a Thread constructor is the standard approach.', 1),
(1, 'What will happen if a checked exception is not handled or declared in a method?', 'MCQ', 'HARD', 'B', 'Checked exceptions must be either caught or declared with "throws"; otherwise the code fails to compile.', 2),
(1, 'ArrayList in Java is backed internally by a dynamically resizable array.', 'TRUE_FALSE', 'MEDIUM', 'TRUE', 'ArrayList uses an internal array that grows automatically when capacity is exceeded.', 1),
(1, 'Describe the concept of garbage collection in Java.', 'DESCRIPTIVE', 'MEDIUM', 'Garbage collection is the automatic process by which the JVM reclaims memory occupied by objects that are no longer reachable from any live thread or static reference, freeing developers from manual memory deallocation.', 'Tests JVM memory management understanding.', 2);

INSERT INTO question_options (question_id, option_label, option_text, is_correct) VALUES
((SELECT question_id FROM questions WHERE question_text = 'Which keyword is used to inherit a class in Java?'), 'A', 'implements', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which keyword is used to inherit a class in Java?'), 'B', 'extends', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'Which keyword is used to inherit a class in Java?'), 'C', 'inherits', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which keyword is used to inherit a class in Java?'), 'D', 'super', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'Which of these is NOT a primitive data type in Java?'), 'A', 'int', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which of these is NOT a primitive data type in Java?'), 'B', 'boolean', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which of these is NOT a primitive data type in Java?'), 'C', 'String', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'Which of these is NOT a primitive data type in Java?'), 'D', 'char', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'What is the default value of a boolean instance variable in Java?'), 'A', 'false', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'What is the default value of a boolean instance variable in Java?'), 'B', 'true', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is the default value of a boolean instance variable in Java?'), 'C', '0', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is the default value of a boolean instance variable in Java?'), 'D', 'null', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'Which collection class allows duplicate elements and maintains insertion order?'), 'A', 'HashSet', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which collection class allows duplicate elements and maintains insertion order?'), 'B', 'ArrayList', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'Which collection class allows duplicate elements and maintains insertion order?'), 'C', 'TreeSet', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which collection class allows duplicate elements and maintains insertion order?'), 'D', 'HashMap', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'What does the "final" keyword do when applied to a variable?'), 'A', 'Prevents reassignment after initial assignment', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'What does the "final" keyword do when applied to a variable?'), 'B', 'Makes the variable static', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What does the "final" keyword do when applied to a variable?'), 'C', 'Makes the variable thread-safe', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What does the "final" keyword do when applied to a variable?'), 'D', 'Deletes the variable after use', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'Which exception is thrown when dividing an integer by zero in Java?'), 'A', 'NullPointerException', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which exception is thrown when dividing an integer by zero in Java?'), 'B', 'NumberFormatException', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which exception is thrown when dividing an integer by zero in Java?'), 'C', 'ArithmeticException', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'Which exception is thrown when dividing an integer by zero in Java?'), 'D', 'IllegalStateException', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'Which of the following correctly creates a thread by implementing Runnable?'), 'A', 'new Thread(new MyRunnable()).start();', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'Which of the following correctly creates a thread by implementing Runnable?'), 'B', 'new MyRunnable().run();', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which of the following correctly creates a thread by implementing Runnable?'), 'C', 'Thread.start(new MyRunnable());', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which of the following correctly creates a thread by implementing Runnable?'), 'D', 'Runnable.start();', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'What will happen if a checked exception is not handled or declared in a method?'), 'A', 'It is silently ignored at runtime', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What will happen if a checked exception is not handled or declared in a method?'), 'B', 'The code fails to compile', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'What will happen if a checked exception is not handled or declared in a method?'), 'C', 'It is automatically converted to a RuntimeException', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What will happen if a checked exception is not handled or declared in a method?'), 'D', 'The JVM crashes at startup', FALSE);

-- ---------------------------------------------------------------------
-- DATABASE & SQL QUESTIONS (topic 2)
-- ---------------------------------------------------------------------
INSERT INTO questions (topic_id, question_text, question_type, difficulty, correct_answer, explanation, marks) VALUES
(2, 'Which SQL clause is used to filter rows before grouping?', 'MCQ', 'EASY', 'B', 'WHERE filters rows before GROUP BY is applied; HAVING filters after grouping.', 1),
(2, 'A PRIMARY KEY column can contain NULL values.', 'TRUE_FALSE', 'EASY', 'FALSE', 'PRIMARY KEY implies NOT NULL and UNIQUE; NULLs are not allowed.', 1),
(2, 'Which SQL command is used to remove a table structure entirely, including its data?', 'MCQ', 'EASY', 'C', 'DROP TABLE removes the table definition and all its data permanently.', 1),
(2, 'What does ACID stand for in database transactions?', 'MCQ', 'MEDIUM', 'A', 'ACID = Atomicity, Consistency, Isolation, Durability - the four guarantees of a reliable transaction.', 2),
(2, 'A JOIN combines rows from two or more tables based on a related column.', 'TRUE_FALSE', 'EASY', 'TRUE', 'JOIN operations combine rows using a related key/column between tables.', 1),
(2, 'Which normal form eliminates transitive dependency?', 'MCQ', 'MEDIUM', 'C', 'Third Normal Form (3NF) removes transitive dependencies on the primary key.', 2),
(2, 'What is the purpose of an index in a database table?', 'MCQ', 'MEDIUM', 'B', 'Indexes speed up data retrieval (SELECT queries) at some cost to write performance.', 1),
(2, 'INNER JOIN returns rows only when there is a match in both joined tables.', 'TRUE_FALSE', 'MEDIUM', 'TRUE', 'INNER JOIN excludes rows without a matching counterpart in the other table.', 1),
(2, 'Which JDBC interface is used to execute parameterized SQL queries safely against SQL injection?', 'MCQ', 'MEDIUM', 'A', 'PreparedStatement pre-compiles the SQL and safely binds parameters, preventing injection.', 2),
(2, 'Explain the difference between DELETE, TRUNCATE and DROP in SQL.', 'DESCRIPTIVE', 'HARD', 'DELETE removes specific rows (can be rolled back, fires triggers, keeps table structure); TRUNCATE removes all rows quickly without logging individual row deletions (resets identity, minimal rollback support); DROP removes the entire table structure and its data permanently.', 'Tests deep SQL DDL/DML understanding.', 3),
(2, 'What is database normalization and why is it used?', 'DESCRIPTIVE', 'MEDIUM', 'Normalization is the process of organizing tables and columns to minimize data redundancy and avoid update/insert/delete anomalies, typically by decomposing tables according to normal forms (1NF, 2NF, 3NF).', 'Tests conceptual database design knowledge.', 2),
(2, 'Which SQL keyword is used to sort query results?', 'MCQ', 'EASY', 'D', 'ORDER BY sorts the result set by one or more columns.', 1),
(2, 'A transaction that has been COMMITTED can still be rolled back afterwards.', 'TRUE_FALSE', 'MEDIUM', 'FALSE', 'Once a transaction is committed, its changes are permanent and cannot be rolled back.', 1),
(2, 'What isolation level prevents dirty reads but still allows non-repeatable reads?', 'MCQ', 'HARD', 'B', 'READ COMMITTED prevents dirty reads by only allowing reads of committed data, but non-repeatable reads can still occur.', 2),
(2, 'Explain what a foreign key constraint enforces in a relational database.', 'DESCRIPTIVE', 'MEDIUM', 'A foreign key enforces referential integrity by ensuring that a value in one table must correspond to an existing value (usually a primary key) in another (or the same) table, preventing orphaned records.', 'Tests referential integrity understanding.', 2);

INSERT INTO question_options (question_id, option_label, option_text, is_correct) VALUES
((SELECT question_id FROM questions WHERE question_text = 'Which SQL clause is used to filter rows before grouping?'), 'A', 'HAVING', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which SQL clause is used to filter rows before grouping?'), 'B', 'WHERE', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'Which SQL clause is used to filter rows before grouping?'), 'C', 'GROUP BY', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which SQL clause is used to filter rows before grouping?'), 'D', 'ORDER BY', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'Which SQL command is used to remove a table structure entirely, including its data?'), 'A', 'DELETE', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which SQL command is used to remove a table structure entirely, including its data?'), 'B', 'TRUNCATE', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which SQL command is used to remove a table structure entirely, including its data?'), 'C', 'DROP', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'Which SQL command is used to remove a table structure entirely, including its data?'), 'D', 'REMOVE', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'What does ACID stand for in database transactions?'), 'A', 'Atomicity, Consistency, Isolation, Durability', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'What does ACID stand for in database transactions?'), 'B', 'Availability, Consistency, Integrity, Durability', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What does ACID stand for in database transactions?'), 'C', 'Atomicity, Concurrency, Isolation, Data-integrity', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What does ACID stand for in database transactions?'), 'D', 'Access, Control, Isolation, Durability', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'Which normal form eliminates transitive dependency?'), 'A', '1NF', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which normal form eliminates transitive dependency?'), 'B', '2NF', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which normal form eliminates transitive dependency?'), 'C', '3NF', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'Which normal form eliminates transitive dependency?'), 'D', 'BCNF', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'What is the purpose of an index in a database table?'), 'A', 'To enforce uniqueness only', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is the purpose of an index in a database table?'), 'B', 'To speed up data retrieval', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'What is the purpose of an index in a database table?'), 'C', 'To compress table storage', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is the purpose of an index in a database table?'), 'D', 'To encrypt column data', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'Which JDBC interface is used to execute parameterized SQL queries safely against SQL injection?'), 'A', 'PreparedStatement', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'Which JDBC interface is used to execute parameterized SQL queries safely against SQL injection?'), 'B', 'Statement', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which JDBC interface is used to execute parameterized SQL queries safely against SQL injection?'), 'C', 'ResultSet', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which JDBC interface is used to execute parameterized SQL queries safely against SQL injection?'), 'D', 'CallableStatement', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'Which SQL keyword is used to sort query results?'), 'A', 'SORT BY', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which SQL keyword is used to sort query results?'), 'B', 'GROUP BY', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which SQL keyword is used to sort query results?'), 'C', 'FILTER BY', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which SQL keyword is used to sort query results?'), 'D', 'ORDER BY', TRUE),

((SELECT question_id FROM questions WHERE question_text = 'What isolation level prevents dirty reads but still allows non-repeatable reads?'), 'A', 'READ UNCOMMITTED', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What isolation level prevents dirty reads but still allows non-repeatable reads?'), 'B', 'READ COMMITTED', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'What isolation level prevents dirty reads but still allows non-repeatable reads?'), 'C', 'REPEATABLE READ', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What isolation level prevents dirty reads but still allows non-repeatable reads?'), 'D', 'SERIALIZABLE', FALSE);

-- ---------------------------------------------------------------------
-- DATA STRUCTURES & ALGORITHMS QUESTIONS (topic 3)
-- ---------------------------------------------------------------------
INSERT INTO questions (topic_id, question_text, question_type, difficulty, correct_answer, explanation, marks) VALUES
(3, 'What is the time complexity of binary search on a sorted array of n elements?', 'MCQ', 'EASY', 'B', 'Binary search halves the search space each step, giving O(log n) complexity.', 1),
(3, 'A stack follows the First-In-First-Out (FIFO) principle.', 'TRUE_FALSE', 'EASY', 'FALSE', 'A stack follows Last-In-First-Out (LIFO); a queue follows FIFO.', 1),
(3, 'Which data structure is best suited to implement a priority queue efficiently?', 'MCQ', 'MEDIUM', 'C', 'A heap (binary heap) provides O(log n) insert and O(log n) extract-min/max, ideal for priority queues.', 2),
(3, 'What is the worst-case time complexity of QuickSort?', 'MCQ', 'MEDIUM', 'D', 'QuickSort degrades to O(n^2) in the worst case (e.g., already sorted input with a poor pivot choice).', 2),
(3, 'A binary search tree guarantees O(log n) search time in all cases.', 'TRUE_FALSE', 'MEDIUM', 'FALSE', 'An unbalanced BST can degrade to O(n) in the worst case; only balanced BSTs guarantee O(log n).', 1),
(3, 'Which traversal of a binary tree visits nodes in sorted order for a BST?', 'MCQ', 'MEDIUM', 'A', 'In-order traversal (left, root, right) visits nodes of a BST in ascending sorted order.', 1),
(3, 'What is the space complexity of an iterative algorithm that uses O(1) extra memory?', 'MCQ', 'EASY', 'A', 'O(1) means constant extra space regardless of input size.', 1),
(3, 'A hash table can achieve average-case O(1) time complexity for insertion and lookup.', 'TRUE_FALSE', 'MEDIUM', 'TRUE', 'With a good hash function and low load factor, hash tables average O(1) for insert/lookup.', 1),
(3, 'Which algorithmic technique does merge sort use?', 'MCQ', 'MEDIUM', 'B', 'Merge sort is a classic divide-and-conquer algorithm.', 1),
(3, 'Explain the difference between a linked list and an array in terms of memory layout and access time.', 'DESCRIPTIVE', 'HARD', 'Arrays store elements in contiguous memory allowing O(1) random access via index but costly insert/delete in the middle (O(n) shifting); linked lists store elements as nodes with pointers scattered in memory, giving O(1) insert/delete at a known position but O(n) access time since traversal is required.', 'Tests understanding of fundamental data structure trade-offs.', 3),
(3, 'What is dynamic programming and when is it useful?', 'DESCRIPTIVE', 'HARD', 'Dynamic programming is an optimization technique that solves complex problems by breaking them into overlapping subproblems, solving each subproblem once and storing (memoizing) results to avoid redundant computation; useful when a problem exhibits optimal substructure and overlapping subproblems, e.g. Fibonacci, knapsack, shortest path.', 'Tests algorithmic design knowledge.', 3),
(3, 'What is the time complexity of inserting an element at the beginning of an ArrayList in Java?', 'MCQ', 'MEDIUM', 'C', 'Inserting at the front requires shifting all existing elements, giving O(n) time.', 2),
(3, 'A graph with no cycles and n-1 edges connecting n nodes is called a tree.', 'TRUE_FALSE', 'MEDIUM', 'TRUE', 'This is the definition of a (connected, acyclic) tree in graph theory.', 1),
(3, 'Which sorting algorithm is stable and has O(n log n) worst-case complexity?', 'MCQ', 'HARD', 'A', 'Merge sort is stable (preserves relative order of equal elements) and guarantees O(n log n) in the worst case.', 2),
(3, 'Describe how Breadth-First Search (BFS) differs from Depth-First Search (DFS) in graph traversal.', 'DESCRIPTIVE', 'MEDIUM', 'BFS explores all neighbors at the current depth before moving to nodes at the next depth level, using a queue, and is well-suited for finding shortest paths in unweighted graphs; DFS explores as far as possible along a branch before backtracking, using a stack or recursion, and is well-suited for tasks like cycle detection and topological sorting.', 'Tests graph traversal understanding.', 2);

INSERT INTO question_options (question_id, option_label, option_text, is_correct) VALUES
((SELECT question_id FROM questions WHERE question_text = 'What is the time complexity of binary search on a sorted array of n elements?'), 'A', 'O(n)', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is the time complexity of binary search on a sorted array of n elements?'), 'B', 'O(log n)', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'What is the time complexity of binary search on a sorted array of n elements?'), 'C', 'O(n log n)', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is the time complexity of binary search on a sorted array of n elements?'), 'D', 'O(1)', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'Which data structure is best suited to implement a priority queue efficiently?'), 'A', 'Array', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which data structure is best suited to implement a priority queue efficiently?'), 'B', 'Linked List', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which data structure is best suited to implement a priority queue efficiently?'), 'C', 'Heap', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'Which data structure is best suited to implement a priority queue efficiently?'), 'D', 'Stack', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'What is the worst-case time complexity of QuickSort?'), 'A', 'O(n)', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is the worst-case time complexity of QuickSort?'), 'B', 'O(log n)', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is the worst-case time complexity of QuickSort?'), 'C', 'O(n log n)', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is the worst-case time complexity of QuickSort?'), 'D', 'O(n^2)', TRUE),

((SELECT question_id FROM questions WHERE question_text = 'Which traversal of a binary tree visits nodes in sorted order for a BST?'), 'A', 'In-order', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'Which traversal of a binary tree visits nodes in sorted order for a BST?'), 'B', 'Pre-order', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which traversal of a binary tree visits nodes in sorted order for a BST?'), 'C', 'Post-order', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which traversal of a binary tree visits nodes in sorted order for a BST?'), 'D', 'Level-order', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'What is the space complexity of an iterative algorithm that uses O(1) extra memory?'), 'A', 'O(1)', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'What is the space complexity of an iterative algorithm that uses O(1) extra memory?'), 'B', 'O(n)', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is the space complexity of an iterative algorithm that uses O(1) extra memory?'), 'C', 'O(log n)', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is the space complexity of an iterative algorithm that uses O(1) extra memory?'), 'D', 'O(n^2)', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'Which algorithmic technique does merge sort use?'), 'A', 'Greedy', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which algorithmic technique does merge sort use?'), 'B', 'Divide and Conquer', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'Which algorithmic technique does merge sort use?'), 'C', 'Dynamic Programming', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which algorithmic technique does merge sort use?'), 'D', 'Backtracking', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'What is the time complexity of inserting an element at the beginning of an ArrayList in Java?'), 'A', 'O(1)', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is the time complexity of inserting an element at the beginning of an ArrayList in Java?'), 'B', 'O(log n)', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is the time complexity of inserting an element at the beginning of an ArrayList in Java?'), 'C', 'O(n)', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'What is the time complexity of inserting an element at the beginning of an ArrayList in Java?'), 'D', 'O(n^2)', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'Which sorting algorithm is stable and has O(n log n) worst-case complexity?'), 'A', 'Merge Sort', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'Which sorting algorithm is stable and has O(n log n) worst-case complexity?'), 'B', 'QuickSort', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which sorting algorithm is stable and has O(n log n) worst-case complexity?'), 'C', 'Selection Sort', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which sorting algorithm is stable and has O(n log n) worst-case complexity?'), 'D', 'Bubble Sort', FALSE);

-- ---------------------------------------------------------------------
-- OPERATING SYSTEMS QUESTIONS (topic 4)
-- ---------------------------------------------------------------------
INSERT INTO questions (topic_id, question_text, question_type, difficulty, correct_answer, explanation, marks) VALUES
(4, 'What is a deadlock in operating systems?', 'MCQ', 'MEDIUM', 'B', 'A deadlock is a state where two or more processes are blocked forever, each waiting on a resource held by another.', 2),
(4, 'A process and a thread share the same memory address space by default.', 'TRUE_FALSE', 'EASY', 'FALSE', 'Threads within the same process share memory; separate processes have their own isolated address spaces by default.', 1),
(4, 'Which scheduling algorithm can cause starvation of low-priority processes?', 'MCQ', 'MEDIUM', 'A', 'Priority scheduling can starve low-priority processes if high-priority ones keep arriving.', 1),
(4, 'What is the primary purpose of virtual memory?', 'MCQ', 'EASY', 'C', 'Virtual memory lets processes use more memory than physically available by using disk as an extension, and provides process isolation.', 1),
(4, 'A context switch involves saving and restoring the state (registers, program counter) of a process/thread.', 'TRUE_FALSE', 'MEDIUM', 'TRUE', 'Context switching saves the current execution state and loads another so the CPU can switch tasks.', 1),
(4, 'Which of the four necessary conditions for deadlock states that a resource can only be held by one process at a time?', 'MCQ', 'HARD', 'A', 'Mutual exclusion means a resource is non-shareable and held by only one process at a time.', 2),
(4, 'Explain the difference between a process and a thread.', 'DESCRIPTIVE', 'MEDIUM', 'A process is an independent execution unit with its own memory space, resources and address space managed by the OS; a thread is a lightweight unit of execution within a process that shares the process''s memory and resources with other threads, making thread creation and context switching cheaper than for processes.', 'Tests OS fundamentals.', 2),
(4, 'What is thrashing in the context of virtual memory?', 'MCQ', 'HARD', 'D', 'Thrashing occurs when excessive paging/swapping consumes most CPU time, drastically reducing actual throughput.', 2);

INSERT INTO question_options (question_id, option_label, option_text, is_correct) VALUES
((SELECT question_id FROM questions WHERE question_text = 'What is a deadlock in operating systems?'), 'A', 'A process using final release CPU', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is a deadlock in operating systems?'), 'B', 'Processes waiting indefinitely for resources held by each other', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'What is a deadlock in operating systems?'), 'C', 'A crashed process', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is a deadlock in operating systems?'), 'D', 'A process running out of memory', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'Which scheduling algorithm can cause starvation of low-priority processes?'), 'A', 'Priority Scheduling', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'Which scheduling algorithm can cause starvation of low-priority processes?'), 'B', 'Round Robin', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which scheduling algorithm can cause starvation of low-priority processes?'), 'C', 'First-Come-First-Served', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which scheduling algorithm can cause starvation of low-priority processes?'), 'D', 'Shortest Job First (non-preemptive, single burst)', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'What is the primary purpose of virtual memory?'), 'A', 'To speed up the CPU clock', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is the primary purpose of virtual memory?'), 'B', 'To compress files on disk', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is the primary purpose of virtual memory?'), 'C', 'To let processes use more memory than physically available and provide isolation', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'What is the primary purpose of virtual memory?'), 'D', 'To manage network sockets', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'Which of the four necessary conditions for deadlock states that a resource can only be held by one process at a time?'), 'A', 'Mutual Exclusion', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'Which of the four necessary conditions for deadlock states that a resource can only be held by one process at a time?'), 'B', 'Hold and Wait', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which of the four necessary conditions for deadlock states that a resource can only be held by one process at a time?'), 'C', 'No Preemption', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which of the four necessary conditions for deadlock states that a resource can only be held by one process at a time?'), 'D', 'Circular Wait', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'What is thrashing in the context of virtual memory?'), 'A', 'A CPU overheating issue', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is thrashing in the context of virtual memory?'), 'B', 'A type of malware', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is thrashing in the context of virtual memory?'), 'C', 'Excess disk fragmentation', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is thrashing in the context of virtual memory?'), 'D', 'Excessive paging that drastically reduces throughput', TRUE);

-- ---------------------------------------------------------------------
-- COMPUTER NETWORKS QUESTIONS (topic 5)
-- ---------------------------------------------------------------------
INSERT INTO questions (topic_id, question_text, question_type, difficulty, correct_answer, explanation, marks) VALUES
(5, 'How many layers does the OSI model have?', 'MCQ', 'EASY', 'C', 'The OSI model has 7 layers, from Physical to Application.', 1),
(5, 'TCP is a connection-oriented protocol while UDP is connectionless.', 'TRUE_FALSE', 'EASY', 'TRUE', 'TCP establishes a connection (three-way handshake) and guarantees delivery; UDP does not.', 1),
(5, 'Which protocol is used to resolve a domain name to an IP address?', 'MCQ', 'EASY', 'A', 'DNS (Domain Name System) resolves human-readable domain names to IP addresses.', 1),
(5, 'What is the default port number for HTTPS?', 'MCQ', 'MEDIUM', 'D', 'HTTPS uses port 443 by default; HTTP uses port 80.', 1),
(5, 'A firewall filters network traffic based on predefined security rules.', 'TRUE_FALSE', 'EASY', 'TRUE', 'Firewalls inspect and filter incoming/outgoing traffic against configured rules.', 1),
(5, 'Explain the three-way handshake used to establish a TCP connection.', 'DESCRIPTIVE', 'MEDIUM', 'The TCP three-way handshake consists of: (1) the client sends a SYN packet to request a connection, (2) the server responds with a SYN-ACK acknowledging the request and sending its own synchronization, and (3) the client responds with an ACK, after which the connection is established and data transfer can begin.', 'Tests networking fundamentals.', 2),
(5, 'Which HTTP status code indicates that a requested resource was not found?', 'MCQ', 'EASY', 'B', 'HTTP 404 Not Found indicates the server could not find the requested resource.', 1);

INSERT INTO question_options (question_id, option_label, option_text, is_correct) VALUES
((SELECT question_id FROM questions WHERE question_text = 'How many layers does the OSI model have?'), 'A', '4', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'How many layers does the OSI model have?'), 'B', '5', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'How many layers does the OSI model have?'), 'C', '7', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'How many layers does the OSI model have?'), 'D', '9', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'Which protocol is used to resolve a domain name to an IP address?'), 'A', 'DNS', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'Which protocol is used to resolve a domain name to an IP address?'), 'B', 'FTP', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which protocol is used to resolve a domain name to an IP address?'), 'C', 'SMTP', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which protocol is used to resolve a domain name to an IP address?'), 'D', 'SNMP', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'What is the default port number for HTTPS?'), 'A', '21', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is the default port number for HTTPS?'), 'B', '25', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is the default port number for HTTPS?'), 'C', '80', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'What is the default port number for HTTPS?'), 'D', '443', TRUE),

((SELECT question_id FROM questions WHERE question_text = 'Which HTTP status code indicates that a requested resource was not found?'), 'A', '200', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which HTTP status code indicates that a requested resource was not found?'), 'B', '404', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'Which HTTP status code indicates that a requested resource was not found?'), 'C', '500', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which HTTP status code indicates that a requested resource was not found?'), 'D', '301', FALSE);

-- ---------------------------------------------------------------------
-- OBJECT-ORIENTED DESIGN QUESTIONS (topic 6)
-- ---------------------------------------------------------------------
INSERT INTO questions (topic_id, question_text, question_type, difficulty, correct_answer, explanation, marks) VALUES
(6, 'Which OOP principle is demonstrated by hiding internal implementation details and exposing only necessary interfaces?', 'MCQ', 'EASY', 'A', 'Encapsulation hides internal state and requires interaction through well-defined interfaces/methods.', 1),
(6, 'Polymorphism allows a single interface to represent different underlying data types or behaviors.', 'TRUE_FALSE', 'EASY', 'TRUE', 'Polymorphism lets objects of different classes be treated through a common interface, each responding in its own way.', 1),
(6, 'Which design pattern ensures a class has only one instance and provides a global point of access to it?', 'MCQ', 'MEDIUM', 'B', 'The Singleton pattern restricts instantiation of a class to a single object.', 1),
(6, 'The "S" in SOLID principles stands for the Single Responsibility Principle.', 'TRUE_FALSE', 'MEDIUM', 'TRUE', 'Single Responsibility Principle: a class should have only one reason to change.', 1),
(6, 'Which design pattern is used to create objects without specifying the exact class to instantiate?', 'MCQ', 'MEDIUM', 'C', 'The Factory pattern delegates object creation logic to a separate method/class rather than using "new" directly.', 2),
(6, 'Explain the Open/Closed Principle from SOLID.', 'DESCRIPTIVE', 'HARD', 'The Open/Closed Principle states that software entities (classes, modules, functions) should be open for extension but closed for modification, meaning new functionality should be added by extending existing code (e.g., via inheritance or interfaces) rather than altering already-tested code, reducing the risk of introducing bugs.', 'Tests design principle depth.', 3),
(6, 'What is the main difference between method overloading and method overriding?', 'DESCRIPTIVE', 'MEDIUM', 'Method overloading occurs within the same class with methods sharing a name but differing in parameter list (resolved at compile time / static polymorphism), while method overriding occurs when a subclass provides a specific implementation of a method already defined in its superclass with the same signature (resolved at runtime / dynamic polymorphism).', 'Tests OOP fundamentals.', 2);

INSERT INTO question_options (question_id, option_label, option_text, is_correct) VALUES
((SELECT question_id FROM questions WHERE question_text = 'Which OOP principle is demonstrated by hiding internal implementation details and exposing only necessary interfaces?'), 'A', 'Encapsulation', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'Which OOP principle is demonstrated by hiding internal implementation details and exposing only necessary interfaces?'), 'B', 'Inheritance', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which OOP principle is demonstrated by hiding internal implementation details and exposing only necessary interfaces?'), 'C', 'Polymorphism', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which OOP principle is demonstrated by hiding internal implementation details and exposing only necessary interfaces?'), 'D', 'Abstraction', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'Which design pattern ensures a class has only one instance and provides a global point of access to it?'), 'A', 'Factory', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which design pattern ensures a class has only one instance and provides a global point of access to it?'), 'B', 'Singleton', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'Which design pattern ensures a class has only one instance and provides a global point of access to it?'), 'C', 'Observer', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which design pattern ensures a class has only one instance and provides a global point of access to it?'), 'D', 'Builder', FALSE),

((SELECT question_id FROM questions WHERE question_text = 'Which design pattern is used to create objects without specifying the exact class to instantiate?'), 'A', 'Adapter', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which design pattern is used to create objects without specifying the exact class to instantiate?'), 'B', 'Decorator', FALSE),
((SELECT question_id FROM questions WHERE question_text = 'Which design pattern is used to create objects without specifying the exact class to instantiate?'), 'C', 'Factory', TRUE),
((SELECT question_id FROM questions WHERE question_text = 'Which design pattern is used to create objects without specifying the exact class to instantiate?'), 'D', 'Strategy', FALSE);

-- ---------------------------------------------------------------------
-- CURRENT RELEASE: role -> required-skill taxonomy for Resume AI Analysis'
-- "missing skills" feature and the resume-coverage readiness component.
-- ---------------------------------------------------------------------
-- priority_weight (1-10, higher = more critical to the role) drives the
-- Resume-Role Match feature's "priority gaps" ranking (service.ResumeService
-- #computeRoleMatch): core languages/CS fundamentals/databases for the role
-- are weighted highest, frameworks in the middle, and supporting tooling
-- lowest, so a missing core skill always surfaces before a missing tool.
INSERT INTO role_skill_requirements (role_name, skill_name, category, priority_weight) VALUES
('Backend Developer', 'Java', 'Programming Languages', 9),
('Backend Developer', 'SQL', 'Programming Languages', 9),
('Backend Developer', 'DBMS', 'Databases', 8),
('Backend Developer', 'Data Structures', 'CS Fundamentals', 9),
('Backend Developer', 'Algorithms', 'CS Fundamentals', 8),
('Backend Developer', 'OOP', 'CS Fundamentals', 8),
('Backend Developer', 'REST API', 'CS Fundamentals', 7),
('Backend Developer', 'Spring Boot', 'Frameworks & Libraries', 6),
('Backend Developer', 'Git', 'Cloud & DevOps', 5),
('Backend Developer', 'Docker', 'Cloud & DevOps', 4),
('Full Stack Developer', 'Java', 'Programming Languages', 8),
('Full Stack Developer', 'JavaScript', 'Programming Languages', 9),
('Full Stack Developer', 'SQL', 'Programming Languages', 8),
('Full Stack Developer', 'React', 'Frameworks & Libraries', 7),
('Full Stack Developer', 'Node.js', 'Frameworks & Libraries', 6),
('Full Stack Developer', 'REST API', 'CS Fundamentals', 7),
('Full Stack Developer', 'OOP', 'CS Fundamentals', 7),
('Full Stack Developer', 'Git', 'Cloud & DevOps', 5),
('Frontend Developer', 'JavaScript', 'Programming Languages', 9),
('Frontend Developer', 'TypeScript', 'Programming Languages', 7),
('Frontend Developer', 'React', 'Frameworks & Libraries', 8),
('Frontend Developer', 'Angular', 'Frameworks & Libraries', 6),
('Frontend Developer', 'REST API', 'CS Fundamentals', 6),
('Frontend Developer', 'Git', 'Cloud & DevOps', 5),
('Data Analyst', 'SQL', 'Programming Languages', 9),
('Data Analyst', 'Python', 'Programming Languages', 9),
('Data Analyst', 'DBMS', 'Databases', 8),
('Data Analyst', 'Data Structures', 'CS Fundamentals', 6),
('DevOps Engineer', 'Linux', 'Cloud & DevOps', 8),
('DevOps Engineer', 'Docker', 'Cloud & DevOps', 8),
('DevOps Engineer', 'Kubernetes', 'Cloud & DevOps', 8),
('DevOps Engineer', 'AWS', 'Cloud & DevOps', 7),
('DevOps Engineer', 'CI/CD', 'Cloud & DevOps', 7),
('DevOps Engineer', 'Git', 'Cloud & DevOps', 5),
('DevOps Engineer', 'Jenkins', 'Cloud & DevOps', 5);

-- ---------------------------------------------------------------------
-- final release MILESTONE: gamification badge catalog
-- ---------------------------------------------------------------------
INSERT INTO badge_catalog (badge_code, badge_name, description, criteria) VALUES
('FIRST_STEPS',       'First Steps',          'Completed your very first assessment.',                 'Complete 1 assessment'),
('ASSESSMENT_5',      'Getting Serious',      'Completed 5 assessments.',                               'Complete 5 assessments'),
('ASSESSMENT_25',     'Dedicated Learner',    'Completed 25 assessments.',                              'Complete 25 assessments'),
('PERFECT_SCORE',     'Perfectionist',        'Scored final release on an assessment.',                          'Score final release on any assessment'),
('HIGH_ACHIEVER',     'High Achiever',        'Scored 90% or above on an assessment.',                  'Score >= 90% on any assessment'),
('MISTAKE_SLAYER',    'Mistake Slayer',       'Resolved 10 previously incorrect questions.',            'Resolve 10 mistakes'),
('RESUME_UPLOADED',   'Resume Ready',         'Uploaded and analysed your resume.',                     'Upload 1 resume'),
('MOCK_INTERVIEWER',  'Interview Practice',   'Completed your first AI mock interview.',                'Complete 1 mock interview'),
('MOCK_VETERAN',      'Interview Veteran',    'Completed 10 AI mock interviews.',                       'Complete 10 mock interviews'),
('READY_FOR_HIRE',    'Interview Ready',      'Reached an Interview Readiness Score of 80 or above.',   'Readiness score >= 80'),
('STREAK_3',          '3-Day Streak',         'Practiced 3 days in a row.',                             'Reach a 3-day streak'),
('STREAK_7',          '7-Day Streak',         'Practiced 7 days in a row.',                              'Reach a 7-day streak'),
('STREAK_30',         '30-Day Streak',        'Practiced 30 days in a row.',                             'Reach a 30-day streak'),
('ROADMAP_STARTER',   'Roadmap Started',      'Generated your first personalised learning roadmap.',    'Generate 1 roadmap'),
('ROLE_READY',        'Role Ready',           'Reviewed role & company preparation material.',          'View role prep for a target role');

-- ---------------------------------------------------------------------
-- final release MILESTONE: sample company profiles for Role & Company Preparation
-- ---------------------------------------------------------------------
INSERT INTO company_profiles (company_name, industry, interview_process, notes) VALUES
('Google',   'Technology', 'Online assessment -> 2-3 technical phone screens -> onsite (coding, system design, Googleyness/leadership) -> hiring committee.', 'Strong emphasis on data structures & algorithms and clean, tested code.'),
('Amazon',   'E-commerce/Cloud', 'Online assessment -> phone screen -> onsite loop (4-5 rounds) each mapped to a Leadership Principle.', 'Prepare STAR-format behavioural stories for every Leadership Principle.'),
('Microsoft','Technology', 'Recruiter screen -> 1-2 technical phone screens -> onsite loop (coding, design, "as appropriate" round).', 'Emphasis on problem-solving approach and collaborative communication.'),
('TCS',      'IT Services', 'Aptitude test -> technical interview -> HR interview.', 'Focus on CS fundamentals, communication skills, and willingness to relocate.'),
('Infosys',  'IT Services', 'Online test (quant/logical/verbal) -> technical interview -> HR interview.', 'Strong fundamentals in programming and DBMS are expected.');

-- ---------------------------------------------------------------------
-- final release MILESTONE: role & company specific prep questions
-- ---------------------------------------------------------------------
INSERT INTO role_prep_questions (role_name, company_name, question_text, category, difficulty) VALUES
('Backend Developer', NULL,       'Design a REST API for a URL-shortening service. What are the key endpoints and data model?', 'System Design', 'MEDIUM'),
('Backend Developer', NULL,       'How would you handle database connection pooling in a high-traffic Java backend service?', 'Technical', 'MEDIUM'),
('Backend Developer', 'Google',   'Explain how you would design a rate limiter for a public API used by millions of clients.', 'System Design', 'HARD'),
('Backend Developer', 'Amazon',   'Tell me about a time you disagreed with a teammate on a technical decision. What happened? (Leadership Principle: Have Backbone; Disagree and Commit)', 'Behavioral', 'MEDIUM'),
('Full Stack Developer', NULL,    'Walk me through how data flows from a database query to a rendered React component in a typical full-stack app.', 'Technical', 'MEDIUM'),
('Full Stack Developer', 'Amazon','Describe a project where you owned a feature end-to-end, from design to deployment. (Leadership Principle: Ownership)', 'Behavioral', 'MEDIUM'),
('Frontend Developer', NULL,      'How do you optimise the performance of a React application that renders large lists?', 'Technical', 'MEDIUM'),
('Frontend Developer', 'Microsoft','Explain the difference between controlled and uncontrolled components in React, with an example.', 'Technical', 'EASY'),
('Data Analyst', NULL,            'Write a SQL query to find the second-highest salary in an employee table without using LIMIT.', 'Technical', 'MEDIUM'),
('Data Analyst', 'TCS',           'How would you explain a complex data trend to a non-technical stakeholder?', 'Behavioral', 'EASY'),
('DevOps Engineer', NULL,         'Describe how you would design a CI/CD pipeline for a microservices application deployed to Kubernetes.', 'System Design', 'HARD'),
('DevOps Engineer', 'Infosys',    'What steps would you take to troubleshoot a production service that is returning intermittent 500 errors?', 'Technical', 'MEDIUM'),
('Backend Developer', NULL,       'What is the difference between horizontal and vertical scaling, and when would you choose one over the other?', 'System Design', 'EASY'),
('Full Stack Developer', NULL,    'How do you ensure a web application is secure against common vulnerabilities like SQL injection and XSS?', 'Technical', 'MEDIUM'),
('Backend Developer', 'TCS',      'Why do you want to work at this company, and where do you see yourself in five years?', 'Behavioral', 'EASY');
