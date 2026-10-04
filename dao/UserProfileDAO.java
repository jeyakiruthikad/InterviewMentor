package com.careerintelligence.dao;

import com.careerintelligence.database.DatabaseConnection;
import com.careerintelligence.model.UserProfile;

import java.sql.*;
import java.util.Optional;

/**
 * DAO for `user_profiles`. Kept intentionally small for the initial implementation;
 * resumePath / resumeExtractedSkills columns are already present in the
 * supports resume upload, analysis and target-role workflows.
 */
public class UserProfileDAO {

    public Optional<UserProfile> findByUserId(Long userId) throws SQLException {
        String sql = "SELECT * FROM user_profiles WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    /** Creates the profile row if missing, otherwise updates it (upsert). */
    public void save(UserProfile profile) throws SQLException {
        String sql = "INSERT INTO user_profiles (user_id, target_role, target_company, experience_level, bio) "
                + "VALUES (?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE target_role = VALUES(target_role), "
                + "target_company = VALUES(target_company), experience_level = VALUES(experience_level), "
                + "bio = VALUES(bio)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, profile.getUserId());
            ps.setString(2, profile.getTargetRole());
            ps.setString(3, profile.getTargetCompany());
            ps.setString(4, profile.getExperienceLevel() == null ? "FRESHER" : profile.getExperienceLevel());
            ps.setString(5, profile.getBio());
            ps.executeUpdate();
        }
    }

    /**
     * Updates the resume file path and the flattened (comma-separated) list of
     * AI-extracted skills for a user's profile. Called by service.ResumeService
     * after a resume upload has been parsed and analysed. Assumes the profile
     * row already exists (created at registration time by AuthService).
     */
    public void updateResumeInfo(Long userId, String resumePath, String resumeExtractedSkillsCsv) throws SQLException {
        String sql = "UPDATE user_profiles SET resume_path = ?, resume_extracted_skills = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, resumePath);
            ps.setString(2, resumeExtractedSkillsCsv);
            ps.setLong(3, userId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                // Defensive fallback: profile row missing (e.g. very old account) - create it.
                String insertSql = "INSERT INTO user_profiles (user_id, experience_level, resume_path, resume_extracted_skills) "
                        + "VALUES (?, 'FRESHER', ?, ?)";
                try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                    insertPs.setLong(1, userId);
                    insertPs.setString(2, resumePath);
                    insertPs.setString(3, resumeExtractedSkillsCsv);
                    insertPs.executeUpdate();
                }
            }
        }
    }

    private UserProfile mapRow(ResultSet rs) throws SQLException {
        UserProfile profile = new UserProfile();
        profile.setProfileId(rs.getLong("profile_id"));
        profile.setUserId(rs.getLong("user_id"));
        profile.setTargetRole(rs.getString("target_role"));
        profile.setTargetCompany(rs.getString("target_company"));
        profile.setExperienceLevel(rs.getString("experience_level"));
        profile.setBio(rs.getString("bio"));
        profile.setResumePath(rs.getString("resume_path"));
        profile.setResumeExtractedSkills(rs.getString("resume_extracted_skills"));
        return profile;
    }
}
