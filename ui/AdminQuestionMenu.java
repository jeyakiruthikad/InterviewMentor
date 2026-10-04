package com.careerintelligence.ui;

import com.careerintelligence.model.DifficultyLevel;
import com.careerintelligence.model.Question;
import com.careerintelligence.model.QuestionOption;
import com.careerintelligence.model.QuestionType;
import com.careerintelligence.model.Topic;
import com.careerintelligence.model.User;
import com.careerintelligence.service.AdminService;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/** Admin Question Management screen: full CRUD over question-bank questions (incl. MCQ options). */
public class AdminQuestionMenu {

    private final Scanner scanner;
    private final AdminService adminService;

    public AdminQuestionMenu(Scanner scanner, AdminService adminService) {
        this.scanner = scanner;
        this.adminService = adminService;
    }

    public void show(User admin) {
        List<Topic> topics;
        try {
            topics = adminService.getAllTopics(admin);
        } catch (SQLException e) {
            ConsoleIO.printError("Database error: " + e.getMessage());
            ConsoleIO.pause(scanner);
            return;
        }
        if (topics.isEmpty()) {
            ConsoleIO.printInfo("No topics exist yet. Create a topic first (Topic Management).");
            ConsoleIO.pause(scanner);
            return;
        }

        ConsoleIO.printHeader("QUESTION MANAGEMENT - Select a Topic");
        for (Topic t : topics) {
            System.out.printf("%d. %s (%s)%n", t.getTopicId(), t.getTopicName(), t.getCategory());
        }
        int topicId = ConsoleIO.promptInt(scanner, "Enter topic ID (0 to cancel)", 0, Integer.MAX_VALUE);
        if (topicId == 0) return;
        Topic topic = topics.stream().filter(t -> t.getTopicId() == topicId).findFirst().orElse(null);
        if (topic == null) {
            ConsoleIO.printError("No topic with that ID.");
            ConsoleIO.pause(scanner);
            return;
        }

        boolean back = false;
        while (!back) {
            List<Question> questions;
            try {
                questions = adminService.getQuestionsForTopic(admin, topic.getTopicId());
            } catch (SQLException e) {
                ConsoleIO.printError("Database error: " + e.getMessage());
                return;
            }
            ConsoleIO.printHeader("QUESTIONS IN: " + topic.getTopicName());
            printQuestions(questions);

            System.out.println("\n1. Add a question");
            System.out.println("2. Delete a question");
            System.out.println("3. Back");
            int choice = ConsoleIO.promptInt(scanner, "Choose an option", 1, 3);
            switch (choice) {
                case 1 -> addQuestion(admin, topic);
                case 2 -> deleteQuestion(admin, questions);
                case 3 -> back = true;
            }
        }
    }

    private void printQuestions(List<Question> questions) {
        if (questions.isEmpty()) {
            System.out.println("No questions yet in this topic.");
            return;
        }
        for (Question q : questions) {
            System.out.printf("[%d] (%s/%s/%s) %s%n", q.getQuestionId(), q.getQuestionType(), q.getDifficulty(),
                    q.isActive() ? "active" : "inactive", q.getQuestionText());
        }
    }

    private void addQuestion(User admin, Topic topic) {
        String text = ConsoleIO.prompt(scanner, "Question text");
        String typeStr = ConsoleIO.prompt(scanner, "Type (MCQ / TRUE_FALSE / DESCRIPTIVE)").trim().toUpperCase();
        QuestionType type;
        try {
            type = QuestionType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            ConsoleIO.printError("Invalid type.");
            ConsoleIO.pause(scanner);
            return;
        }
        String diffStr = ConsoleIO.prompt(scanner, "Difficulty (EASY / MEDIUM / HARD)").trim().toUpperCase();
        DifficultyLevel difficulty;
        try {
            difficulty = DifficultyLevel.valueOf(diffStr);
        } catch (IllegalArgumentException e) {
            ConsoleIO.printError("Invalid difficulty.");
            ConsoleIO.pause(scanner);
            return;
        }
        int marks = ConsoleIO.promptInt(scanner, "Marks", 1, 100);

        Question question = new Question();
        question.setTopicId(topic.getTopicId());
        question.setQuestionText(text);
        question.setQuestionType(type);
        question.setDifficulty(difficulty);
        question.setMarks(marks);
        question.setActive(true);

        if (type == QuestionType.MCQ) {
            List<QuestionOption> options = new ArrayList<>();
            int count = ConsoleIO.promptInt(scanner, "Number of options (2-6)", 2, 6);
            String correctLabel = null;
            for (int i = 0; i < count; i++) {
                String label = String.valueOf((char) ('A' + i));
                String optText = ConsoleIO.prompt(scanner, "Option " + label + " text");
                QuestionOption opt = new QuestionOption();
                opt.setOptionLabel(label);
                opt.setOptionText(optText);
                options.add(opt);
            }
            correctLabel = ConsoleIO.prompt(scanner, "Correct option label (A, B, C...)").trim().toUpperCase();
            for (QuestionOption opt : options) {
                opt.setCorrect(opt.getOptionLabel().equals(correctLabel));
            }
            question.setOptions(options);
            question.setCorrectAnswer(correctLabel);
        } else if (type == QuestionType.TRUE_FALSE) {
            String answer = ConsoleIO.prompt(scanner, "Correct answer (TRUE / FALSE)").trim().toUpperCase();
            question.setCorrectAnswer(answer);
        } else {
            String modelAnswer = ConsoleIO.prompt(scanner, "Model answer (used for AI evaluation)");
            question.setCorrectAnswer(modelAnswer);
        }
        String explanation = ConsoleIO.prompt(scanner, "Explanation (optional)");
        question.setExplanation(explanation);

        AdminService.Result result = adminService.createQuestion(admin, question);
        printResult(result);
    }

    private void deleteQuestion(User admin, List<Question> questions) {
        if (questions.isEmpty()) {
            ConsoleIO.printInfo("No questions to delete.");
            ConsoleIO.pause(scanner);
            return;
        }
        long id = ConsoleIO.promptInt(scanner, "Enter question ID (0 to cancel)", 0, Integer.MAX_VALUE);
        if (id == 0) return;
        boolean exists = questions.stream().anyMatch(q -> q.getQuestionId() == id);
        if (!exists) {
            ConsoleIO.printError("No question with that ID in this topic.");
            ConsoleIO.pause(scanner);
            return;
        }
        String confirm = ConsoleIO.prompt(scanner, "Type DELETE to permanently remove this question");
        if (!confirm.equalsIgnoreCase("DELETE")) {
            ConsoleIO.printInfo("Cancelled.");
            return;
        }
        AdminService.Result result = adminService.deleteQuestion(admin, id);
        printResult(result);
    }

    private void printResult(AdminService.Result result) {
        if (result.success()) ConsoleIO.printSuccess(result.message());
        else ConsoleIO.printError(result.message());
        ConsoleIO.pause(scanner);
    }
}
