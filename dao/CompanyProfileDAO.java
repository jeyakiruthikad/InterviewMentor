package com.careerintelligence.dao;

import com.careerintelligence.database.DatabaseConnection;
import com.careerintelligence.model.CompanyProfile;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO for `company_profiles`: admin-managed reference info about target
 * companies (interview process, focus areas), used by the Role & Company
 * Preparation feature.
 */
public class CompanyProfileDAO {

    public List<CompanyProfile> findAllActive() throws SQLException {
        String sql = "SELECT * FROM company_profiles WHERE is_active = TRUE ORDER BY company_name";
        return query(sql);
    }

    public List<CompanyProfile> findAll() throws SQLException {
        String sql = "SELECT * FROM company_profiles ORDER BY company_name";
        return query(sql);
    }

    private List<CompanyProfile> query(String sql) throws SQLException {
        List<CompanyProfile> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    public Optional<CompanyProfile> findByName(String companyName) throws SQLException {
        if (companyName == null || companyName.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM company_profiles WHERE LOWER(company_name) = LOWER(?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, companyName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    /** Creates or updates (by unique company_name) a company profile - used by the admin module. */
    public CompanyProfile save(CompanyProfile profile) throws SQLException {
        String sql = "INSERT INTO company_profiles (company_name, industry, interview_process, notes, is_active) "
                + "VALUES (?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE industry = VALUES(industry), interview_process = VALUES(interview_process), "
                + "notes = VALUES(notes), is_active = VALUES(is_active)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, profile.getCompanyName());
            ps.setString(2, profile.getIndustry());
            ps.setString(3, profile.getInterviewProcess());
            ps.setString(4, profile.getNotes());
            ps.setBoolean(5, profile.isActive());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    profile.setCompanyId(keys.getInt(1));
                }
            }
        }
        return profile;
    }

    public boolean delete(int companyId) throws SQLException {
        String sql = "DELETE FROM company_profiles WHERE company_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, companyId);
            return ps.executeUpdate() > 0;
        }
    }

    private CompanyProfile mapRow(ResultSet rs) throws SQLException {
        CompanyProfile c = new CompanyProfile();
        c.setCompanyId(rs.getInt("company_id"));
        c.setCompanyName(rs.getString("company_name"));
        c.setIndustry(rs.getString("industry"));
        c.setInterviewProcess(rs.getString("interview_process"));
        c.setNotes(rs.getString("notes"));
        c.setActive(rs.getBoolean("is_active"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) c.setCreatedAt(createdAt.toLocalDateTime());
        return c;
    }
}
