package com.careerintelligence.dao;

import com.careerintelligence.database.DatabaseConnection;
import com.careerintelligence.model.Topic;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TopicDAO {

    public List<Topic> findAllActive() throws SQLException {
        String sql = "SELECT * FROM topics WHERE is_active = TRUE ORDER BY topic_name";
        List<Topic> topics = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                topics.add(mapRow(rs));
            }
        }
        return topics;
    }

    public Optional<Topic> findById(Integer topicId) throws SQLException {
        String sql = "SELECT * FROM topics WHERE topic_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, topicId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    /** Returns how many active questions exist for a topic (used to validate assessment creation). */
    public int countQuestionsForTopic(Integer topicId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM questions WHERE topic_id = ? AND is_active = TRUE";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, topicId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Case-insensitive exact lookup by topic name, used by service.ResumeService to match an
     *  extracted resume skill's taxonomy topic hint (e.g. "Core Java") onto a real topics row. */
    public Optional<Topic> findByName(String topicName) throws SQLException {
        if (topicName == null || topicName.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM topics WHERE LOWER(topic_name) = LOWER(?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, topicName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    // -----------------------------------------------------------------
    // Admin Module additions (final implementation): topic CRUD
    // -----------------------------------------------------------------

    /** All topics including inactive ones - used by the admin Topic Management screen. */
    public List<Topic> findAllIncludingInactive() throws SQLException {
        String sql = "SELECT * FROM topics ORDER BY topic_name";
        List<Topic> topics = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                topics.add(mapRow(rs));
            }
        }
        return topics;
    }

    public Topic create(Topic topic) throws SQLException {
        String sql = "INSERT INTO topics (topic_name, category, description, is_active) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, topic.getTopicName());
            ps.setString(2, topic.getCategory());
            ps.setString(3, topic.getDescription());
            ps.setBoolean(4, topic.isActive());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    topic.setTopicId(keys.getInt(1));
                }
            }
        }
        return topic;
    }

    public boolean update(Topic topic) throws SQLException {
        String sql = "UPDATE topics SET topic_name = ?, category = ?, description = ?, is_active = ? WHERE topic_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, topic.getTopicName());
            ps.setString(2, topic.getCategory());
            ps.setString(3, topic.getDescription());
            ps.setBoolean(4, topic.isActive());
            ps.setInt(5, topic.getTopicId());
            return ps.executeUpdate() > 0;
        }
    }

    /** Permanently deletes a topic and (via ON DELETE CASCADE) all of its questions. Admin-only, irreversible. */
    public boolean delete(Integer topicId) throws SQLException {
        String sql = "DELETE FROM topics WHERE topic_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, topicId);
            return ps.executeUpdate() > 0;
        }
    }

    private Topic mapRow(ResultSet rs) throws SQLException {
        Topic topic = new Topic();
        topic.setTopicId(rs.getInt("topic_id"));
        topic.setTopicName(rs.getString("topic_name"));
        topic.setCategory(rs.getString("category"));
        topic.setDescription(rs.getString("description"));
        topic.setActive(rs.getBoolean("is_active"));
        return topic;
    }
}
