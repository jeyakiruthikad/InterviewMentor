package com.careerintelligence.service;

import com.careerintelligence.dao.AssessmentDAO;
import com.careerintelligence.dao.GamificationDAO;
import com.careerintelligence.dao.QuestionDAO;
import com.careerintelligence.dao.TopicDAO;
import com.careerintelligence.dao.UserDAO;
import com.careerintelligence.model.Question;
import com.careerintelligence.model.Topic;
import com.careerintelligence.model.User;
import com.careerintelligence.model.UserRole;
import com.careerintelligence.util.InputValidator;

import java.sql.SQLException;
import java.util.List;

/**
 * Business logic for administrative management: user
 * management (activate/deactivate/promote/delete), topic and question
 * CRUD, and cross-user assessment management/oversight. Deliberately
 * thin - it composes the same DAOs the rest of the app already uses (plus
 * their new admin-only methods) rather than introducing a parallel data
 * layer, and every write is validated the same way the current implementation
 * services validate user-facing input.
 *
 * Authentication itself is NOT reimplemented here: an admin logs in
 * through the exact same {@link AuthService#login} used by every other
 * user (same PBKDF2 password hashing, same active-account check). Every
 * UI entry point already confirms {@code user.getRole() == UserRole.ADMIN}
 * before reaching this class (see ui.AdminMenu and its sub-menus) - but
 * every method here that reads or changes admin-only data ALSO calls
 * {@link #assertAdmin} itself, so authorization is enforced at the
 * service layer too, not solely trusted from the UI. This is
 * defense-in-depth: a future UI bug, a new call site that forgets the
 * role check, or a direct unit/integration test calling this service
 * still cannot perform an admin action as a non-admin user.
 */
public class AdminService {

    /** Thrown when a non-admin (or null) user reaches an admin-only operation - see {@link #assertAdmin}. */
    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }

    /**
     * Server-side authorization guard: every admin-only method in this class calls this first.
     * Package-private + static so it is directly unit-testable without a database.
     */
    static void assertAdmin(User actingAdmin) {
        if (actingAdmin == null || actingAdmin.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException("Admin privileges are required to perform this action.");
        }
    }

    private final UserDAO userDAO = new UserDAO();
    private final TopicDAO topicDAO = new TopicDAO();
    private final QuestionDAO questionDAO = new QuestionDAO();
    private final AssessmentDAO assessmentDAO = new AssessmentDAO();
    private final GamificationDAO gamificationDAO = new GamificationDAO();

    /** Simple result wrapper so the UI can show a message without exceptions for control flow. */
    public record Result(boolean success, String message) {
        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }

    // -------------------------------------------------------------
    // User management
    // -------------------------------------------------------------

    public List<User> getAllUsers(User actingAdmin) throws SQLException {
        assertAdmin(actingAdmin);
        return userDAO.findAll();
    }

    public int getTotalUserCount(User actingAdmin) throws SQLException {
        assertAdmin(actingAdmin);
        return userDAO.countAll();
    }

    public int getAdminCount(User actingAdmin) throws SQLException {
        assertAdmin(actingAdmin);
        return userDAO.countByRole(UserRole.ADMIN);
    }

    public Result setUserActive(User actingAdmin, Long targetUserId, boolean active) {
        try {
            assertAdmin(actingAdmin);
            if (actingAdmin.getUserId().equals(targetUserId) && !active) {
                return Result.fail("You cannot deactivate your own account while logged in.");
            }
            boolean updated = userDAO.setActive(targetUserId, active);
            return updated ? Result.ok("User " + (active ? "activated" : "deactivated") + ".")
                    : Result.fail("User not found.");
        } catch (SQLException e) {
            return Result.fail("Database error: " + e.getMessage());
        } catch (UnauthorizedException e) {
            return Result.fail(e.getMessage());
        }
    }

    public Result setUserRole(User actingAdmin, Long targetUserId, UserRole newRole) {
        try {
            assertAdmin(actingAdmin);
            if (actingAdmin.getUserId().equals(targetUserId) && newRole != UserRole.ADMIN) {
                return Result.fail("You cannot remove your own admin role while logged in.");
            }
            if (newRole == UserRole.USER) {
                int remainingAdmins = userDAO.countByRole(UserRole.ADMIN);
                User target = userDAO.findById(targetUserId).orElse(null);
                if (target != null && target.getRole() == UserRole.ADMIN && remainingAdmins <= 1) {
                    return Result.fail("Cannot demote the last remaining admin account.");
                }
            }
            boolean updated = userDAO.setRole(targetUserId, newRole);
            return updated ? Result.ok("Role updated to " + newRole + ".") : Result.fail("User not found.");
        } catch (SQLException e) {
            return Result.fail("Database error: " + e.getMessage());
        } catch (UnauthorizedException e) {
            return Result.fail(e.getMessage());
        }
    }

    public Result deleteUser(User actingAdmin, Long targetUserId) {
        try {
            assertAdmin(actingAdmin);
            if (actingAdmin.getUserId().equals(targetUserId)) {
                return Result.fail("You cannot delete your own account while logged in.");
            }
            boolean deleted = userDAO.delete(targetUserId);
            return deleted ? Result.ok("User and all their data deleted.") : Result.fail("User not found.");
        } catch (SQLException e) {
            return Result.fail("Database error: " + e.getMessage());
        } catch (UnauthorizedException e) {
            return Result.fail(e.getMessage());
        }
    }

    // -------------------------------------------------------------
    // Topic CRUD
    // -------------------------------------------------------------

    public List<Topic> getAllTopics(User actingAdmin) throws SQLException {
        assertAdmin(actingAdmin);
        return topicDAO.findAllIncludingInactive();
    }

    public Result createTopic(User actingAdmin, String topicName, String category, String description) {
        try {
            assertAdmin(actingAdmin);
        } catch (UnauthorizedException e) {
            return Result.fail(e.getMessage());
        }
        if (!InputValidator.isNonEmpty(topicName) || !InputValidator.isNonEmpty(category)) {
            return Result.fail("Topic name and category are required.");
        }
        try {
            Topic topic = new Topic();
            topic.setTopicName(topicName.trim());
            topic.setCategory(category.trim());
            topic.setDescription(description == null ? "" : description.trim());
            topic.setActive(true);
            topicDAO.create(topic);
            return Result.ok("Topic \"" + topic.getTopicName() + "\" created (ID " + topic.getTopicId() + ").");
        } catch (SQLException e) {
            return Result.fail("Database error (topic name may already exist): " + e.getMessage());
        }
    }

    public Result updateTopic(User actingAdmin, Topic topic) {
        try {
            assertAdmin(actingAdmin);
        } catch (UnauthorizedException e) {
            return Result.fail(e.getMessage());
        }
        if (!InputValidator.isNonEmpty(topic.getTopicName()) || !InputValidator.isNonEmpty(topic.getCategory())) {
            return Result.fail("Topic name and category are required.");
        }
        try {
            boolean updated = topicDAO.update(topic);
            return updated ? Result.ok("Topic updated.") : Result.fail("Topic not found.");
        } catch (SQLException e) {
            return Result.fail("Database error: " + e.getMessage());
        }
    }

    public Result deleteTopic(User actingAdmin, Integer topicId) {
        try {
            assertAdmin(actingAdmin);
            boolean deleted = topicDAO.delete(topicId);
            return deleted ? Result.ok("Topic and all its questions deleted.") : Result.fail("Topic not found.");
        } catch (SQLException e) {
            return Result.fail("Database error: " + e.getMessage());
        } catch (UnauthorizedException e) {
            return Result.fail(e.getMessage());
        }
    }

    // -------------------------------------------------------------
    // Question CRUD
    // -------------------------------------------------------------

    public List<Question> getQuestionsForTopic(User actingAdmin, Integer topicId) throws SQLException {
        assertAdmin(actingAdmin);
        return questionDAO.findAllByTopicIncludingInactive(topicId);
    }

    public Result createQuestion(User actingAdmin, Question question) {
        try {
            assertAdmin(actingAdmin);
        } catch (UnauthorizedException e) {
            return Result.fail(e.getMessage());
        }
        Result validation = validateQuestion(question);
        if (!validation.success()) {
            return validation;
        }
        try {
            questionDAO.create(question);
            return Result.ok("Question created (ID " + question.getQuestionId() + ").");
        } catch (SQLException e) {
            return Result.fail("Database error: " + e.getMessage());
        }
    }

    public Result updateQuestion(User actingAdmin, Question question) {
        try {
            assertAdmin(actingAdmin);
        } catch (UnauthorizedException e) {
            return Result.fail(e.getMessage());
        }
        Result validation = validateQuestion(question);
        if (!validation.success()) {
            return validation;
        }
        try {
            boolean updated = questionDAO.update(question);
            if (updated && question.getQuestionType() == com.careerintelligence.model.QuestionType.MCQ) {
                questionDAO.replaceOptions(question.getQuestionId(), question.getOptions());
            }
            return updated ? Result.ok("Question updated.") : Result.fail("Question not found.");
        } catch (SQLException e) {
            return Result.fail("Database error: " + e.getMessage());
        }
    }

    public Result deleteQuestion(User actingAdmin, Long questionId) {
        try {
            assertAdmin(actingAdmin);
            boolean deleted = questionDAO.delete(questionId);
            return deleted ? Result.ok("Question deleted.") : Result.fail("Question not found.");
        } catch (SQLException e) {
            return Result.fail("Database error: " + e.getMessage());
        } catch (UnauthorizedException e) {
            return Result.fail(e.getMessage());
        }
    }

    private Result validateQuestion(Question q) {
        if (q.getTopicId() == null) {
            return Result.fail("A topic must be selected.");
        }
        if (!InputValidator.isNonEmpty(q.getQuestionText())) {
            return Result.fail("Question text is required.");
        }
        if (q.getQuestionType() == null || q.getDifficulty() == null) {
            return Result.fail("Question type and difficulty are required.");
        }
        if (!InputValidator.isNonEmpty(q.getCorrectAnswer())) {
            return Result.fail("A correct answer is required.");
        }
        if (q.getMarks() <= 0) {
            return Result.fail("Marks must be a positive number.");
        }
        if (q.getQuestionType() == com.careerintelligence.model.QuestionType.MCQ) {
            if (q.getOptions() == null || q.getOptions().size() < 2) {
                return Result.fail("MCQ questions need at least 2 options.");
            }
            boolean hasCorrect = q.getOptions().stream().anyMatch(com.careerintelligence.model.QuestionOption::isCorrect);
            if (!hasCorrect) {
                return Result.fail("At least one MCQ option must be marked correct.");
            }
        }
        return Result.ok("valid");
    }

    // -------------------------------------------------------------
    // Assessment management / oversight
    // -------------------------------------------------------------

    public List<AssessmentDAO.AdminAssessmentRow> getRecentAssessments(User actingAdmin, int limit) throws SQLException {
        assertAdmin(actingAdmin);
        return assessmentDAO.findAllForAdmin(limit);
    }

    public int getTotalAssessmentCount(User actingAdmin) throws SQLException {
        assertAdmin(actingAdmin);
        return assessmentDAO.countAllForAdmin();
    }

    public Result deleteAssessment(User actingAdmin, Long assessmentId) {
        try {
            assertAdmin(actingAdmin);
            boolean deleted = assessmentDAO.delete(assessmentId);
            return deleted ? Result.ok("Assessment record deleted.") : Result.fail("Assessment not found.");
        } catch (SQLException e) {
            return Result.fail("Database error: " + e.getMessage());
        } catch (UnauthorizedException e) {
            return Result.fail(e.getMessage());
        }
    }

    // -------------------------------------------------------------
    // Gamification oversight
    // -------------------------------------------------------------

    public List<Object[]> getPointsLeaderboard(User actingAdmin, int limit) throws SQLException {
        assertAdmin(actingAdmin);
        return gamificationDAO.findTopUsersByPoints(limit);
    }
}
