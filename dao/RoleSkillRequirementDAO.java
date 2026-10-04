package com.careerintelligence.dao;

import com.careerintelligence.database.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for `role_skill_requirements`: a small reference taxonomy of which
 * skills are expected for a given target role (e.g. "Backend Developer" ->
 * Java, SQL, Data Structures, ...). Seeded by database/seed_data.sql for
 * a handful of common roles; used by service.ResumeService to compute
 * "missing skills" for the Resume AI Analysis feature and by
 * service.ReadinessScoreService for the resume-coverage readiness component.
 */
public class RoleSkillRequirementDAO {

    /** One role requirement row: the skill, its category, and how critical it is (1-10) to the role. */
    public record Requirement(String skillName, String category, int priorityWeight) {
    }

    /**
     * Full requirement rows (skill + category + priority weight) for a role, used by
     * {@code service.ResumeService#computeRoleMatch} to rank missing/weak skills by
     * how critical each one actually is to the target role, instead of alphabetically.
     */
    public List<Requirement> findRequirementsForRole(String roleName) throws SQLException {
        List<Requirement> result = new ArrayList<>();
        if (roleName == null || roleName.isBlank()) {
            return result;
        }
        String sql = "SELECT skill_name, category, priority_weight FROM role_skill_requirements "
                + "WHERE LOWER(role_name) = LOWER(?) ORDER BY priority_weight DESC, skill_name";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roleName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int weight = rs.getInt("priority_weight");
                    if (rs.wasNull()) {
                        weight = 5;
                    }
                    result.add(new Requirement(rs.getString("skill_name"), rs.getString("category"), weight));
                }
            }
        }
        return result;
    }

    public List<String> findSkillsForRole(String roleName) throws SQLException {
        if (roleName == null || roleName.isBlank()) {
            return new ArrayList<>();
        }
        String sql = "SELECT skill_name FROM role_skill_requirements WHERE LOWER(role_name) = LOWER(?) "
                + "ORDER BY skill_name";
        List<String> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roleName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString("skill_name"));
                }
            }
        }
        return result;
    }

    /** Distinct role names available in the taxonomy, for prompting the user with valid options. */
    public List<String> findAllRoleNames() throws SQLException {
        String sql = "SELECT DISTINCT role_name FROM role_skill_requirements ORDER BY role_name";
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
}
