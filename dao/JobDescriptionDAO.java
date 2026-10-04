package com.careerintelligence.dao;

import com.careerintelligence.database.DatabaseConnection;
import com.careerintelligence.model.JobDescription;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * DAO for `job_descriptions`: one job description a user pasted in for AI
 * analysis, plus the structured fields extracted from it (required/preferred
 * skills, technologies, responsibilities, experience, soft skills). List
 * fields are stored as a simple {@code "|"}-delimited string, consistent
 * with how {@code user_profiles.resume_extracted_skills} already stores a
 * flat skill list elsewhere in this schema - no new dependency needed for
 * something this small.
 */
public class JobDescriptionDAO {

    private static final String DELIM = "\\|";
    private static final String JOIN_DELIM = "|";

    public JobDescription save(JobDescription jd) throws SQLException {
        String sql = "INSERT INTO job_descriptions "
                + "(user_id, title, raw_text, required_skills, preferred_skills, technologies, "
                + "responsibilities, soft_skills, experience_required) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, jd.getUserId());
            ps.setString(2, jd.getTitle());
            ps.setString(3, jd.getRawText());
            ps.setString(4, join(jd.getRequiredSkills()));
            ps.setString(5, join(jd.getPreferredSkills()));
            ps.setString(6, join(jd.getTechnologies()));
            ps.setString(7, join(jd.getResponsibilities()));
            ps.setString(8, join(jd.getSoftSkills()));
            ps.setString(9, jd.getExperienceRequired());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    jd.setId(keys.getLong(1));
                }
            }
        }
        return jd;
    }

    public Optional<JobDescription> findLatestByUser(long userId) throws SQLException {
        String sql = "SELECT * FROM job_descriptions WHERE user_id = ? ORDER BY analyzed_at DESC LIMIT 1";
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

    public Optional<JobDescription> findById(long id) throws SQLException {
        String sql = "SELECT * FROM job_descriptions WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(JOIN_DELIM, values);
    }

    private List<String> split(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return Arrays.stream(stored.split(DELIM)).filter(s -> !s.isBlank()).toList();
    }

    private JobDescription mapRow(ResultSet rs) throws SQLException {
        JobDescription jd = new JobDescription();
        jd.setId(rs.getLong("id"));
        jd.setUserId(rs.getLong("user_id"));
        jd.setTitle(rs.getString("title"));
        jd.setRawText(rs.getString("raw_text"));
        jd.setRequiredSkills(split(rs.getString("required_skills")));
        jd.setPreferredSkills(split(rs.getString("preferred_skills")));
        jd.setTechnologies(split(rs.getString("technologies")));
        jd.setResponsibilities(split(rs.getString("responsibilities")));
        jd.setSoftSkills(split(rs.getString("soft_skills")));
        jd.setExperienceRequired(rs.getString("experience_required"));
        Timestamp analyzedAt = rs.getTimestamp("analyzed_at");
        if (analyzedAt != null) {
            jd.setAnalyzedAt(analyzedAt.toLocalDateTime());
        }
        return jd;
    }
}
