package com.careerintelligence.dao;

import com.careerintelligence.database.DatabaseConnection;
import com.careerintelligence.model.DifficultyLevel;
import com.careerintelligence.model.RolePrepQuestion;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for `role_prep_questions`: the admin-managed bank of role- and/or
 * company-specific interview questions used by the Role & Company
 * Preparation feature. {@code company_name IS NULL} rows apply generically
 * to the role, regardless of target company.
 */
public class RolePrepQuestionDAO {

    /** Questions for a role: generic-for-role rows plus (if given) rows specific to that company. */
    public List<RolePrepQuestion> findForRoleAndCompany(String roleName, String companyName) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM role_prep_questions WHERE is_active = TRUE AND LOWER(role_name) = LOWER(?) "
                        + "AND (company_name IS NULL");
        boolean hasCompany = companyName != null && !companyName.isBlank();
        if (hasCompany) {
            sql.append(" OR LOWER(company_name) = LOWER(?)");
        }
        sql.append(") ORDER BY company_name IS NULL DESC, category, difficulty");

        List<RolePrepQuestion> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, roleName.trim());
            if (hasCompany) {
                ps.setString(2, companyName.trim());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    public List<RolePrepQuestion> findAll() throws SQLException {
        String sql = "SELECT * FROM role_prep_questions ORDER BY role_name, company_name";
        List<RolePrepQuestion> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    public List<String> findDistinctRoleNames() throws SQLException {
        String sql = "SELECT DISTINCT role_name FROM role_prep_questions WHERE is_active = TRUE ORDER BY role_name";
        List<String> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString("role_name"));
            }
        }
        return result;
    }

    /** Admin: creates a new role/company prep question. */
    public RolePrepQuestion create(RolePrepQuestion q) throws SQLException {
        String sql = "INSERT INTO role_prep_questions (role_name, company_name, question_text, category, difficulty, is_active) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, q.getRoleName());
            ps.setString(2, q.getCompanyName());
            ps.setString(3, q.getQuestionText());
            ps.setString(4, q.getCategory());
            ps.setString(5, q.getDifficulty().name());
            ps.setBoolean(6, q.isActive());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    q.setId(keys.getLong(1));
                }
            }
        }
        return q;
    }

    /** Admin: updates an existing prep question. */
    public boolean update(RolePrepQuestion q) throws SQLException {
        String sql = "UPDATE role_prep_questions SET role_name = ?, company_name = ?, question_text = ?, "
                + "category = ?, difficulty = ?, is_active = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, q.getRoleName());
            ps.setString(2, q.getCompanyName());
            ps.setString(3, q.getQuestionText());
            ps.setString(4, q.getCategory());
            ps.setString(5, q.getDifficulty().name());
            ps.setBoolean(6, q.isActive());
            ps.setLong(7, q.getId());
            return ps.executeUpdate() > 0;
        }
    }

    /** Admin: hard-deletes a prep question. */
    public boolean delete(long id) throws SQLException {
        String sql = "DELETE FROM role_prep_questions WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private RolePrepQuestion mapRow(ResultSet rs) throws SQLException {
        RolePrepQuestion q = new RolePrepQuestion();
        q.setId(rs.getLong("id"));
        q.setRoleName(rs.getString("role_name"));
        q.setCompanyName(rs.getString("company_name"));
        q.setQuestionText(rs.getString("question_text"));
        q.setCategory(rs.getString("category"));
        q.setDifficulty(DifficultyLevel.valueOf(rs.getString("difficulty")));
        q.setActive(rs.getBoolean("is_active"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) q.setCreatedAt(createdAt.toLocalDateTime());
        return q;
    }
}
