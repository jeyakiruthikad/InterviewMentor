package com.careerintelligence.dao;

import com.careerintelligence.database.DatabaseConnection;
import com.careerintelligence.model.LearningRoadmap;
import com.careerintelligence.model.RoadmapCategory;
import com.careerintelligence.model.RoadmapItem;
import com.careerintelligence.model.RoadmapItemStatus;
import com.careerintelligence.model.RoadmapPriority;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO for `learning_roadmaps` and `roadmap_items`: persists each generated
 * Personalised Learning Roadmap so a user can revisit it and track
 * progress on individual recommendations (service.RoadmapService).
 */
public class RoadmapDAO {

    public LearningRoadmap save(LearningRoadmap roadmap) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "INSERT INTO learning_roadmaps (user_id, readiness_score, readiness_band, summary) "
                    + "VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, roadmap.getUserId());
                if (roadmap.getReadinessScore() != null) {
                    ps.setDouble(2, roadmap.getReadinessScore());
                } else {
                    ps.setNull(2, Types.DECIMAL);
                }
                ps.setString(3, roadmap.getReadinessBand());
                ps.setString(4, roadmap.getSummary());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        roadmap.setRoadmapId(keys.getLong(1));
                    }
                }
            }

            String itemSql = "INSERT INTO roadmap_items (roadmap_id, item_order, category, priority, title, "
                    + "description, related_topic_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(itemSql, Statement.RETURN_GENERATED_KEYS)) {
                int order = 1;
                for (RoadmapItem item : roadmap.getItems()) {
                    ps.setLong(1, roadmap.getRoadmapId());
                    ps.setInt(2, order++);
                    ps.setString(3, item.getCategory().name());
                    ps.setString(4, item.getPriority().name());
                    ps.setString(5, item.getTitle());
                    ps.setString(6, item.getDescription());
                    if (item.getRelatedTopicId() != null) {
                        ps.setInt(7, item.getRelatedTopicId());
                    } else {
                        ps.setNull(7, Types.INTEGER);
                    }
                    ps.addBatch();
                }
                if (!roadmap.getItems().isEmpty()) {
                    ps.executeBatch();
                }
            }
        }
        return roadmap;
    }

    public Optional<LearningRoadmap> findLatestByUser(long userId) throws SQLException {
        String sql = "SELECT * FROM learning_roadmaps WHERE user_id = ? ORDER BY generated_at DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    LearningRoadmap roadmap = mapRoadmapRow(rs);
                    roadmap.setItems(findItems(conn, roadmap.getRoadmapId()));
                    return Optional.of(roadmap);
                }
            }
        }
        return Optional.empty();
    }

    public void markItemStatus(long itemId, RoadmapItemStatus status) throws SQLException {
        String sql = "UPDATE roadmap_items SET status = ?, completed_at = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            if (status == RoadmapItemStatus.COMPLETED) {
                ps.setTimestamp(2, Timestamp.valueOf(java.time.LocalDateTime.now()));
            } else {
                ps.setNull(2, Types.TIMESTAMP);
            }
            ps.setLong(3, itemId);
            ps.executeUpdate();
        }
    }

    private List<RoadmapItem> findItems(Connection conn, long roadmapId) throws SQLException {
        String sql = "SELECT ri.*, t.topic_name FROM roadmap_items ri "
                + "LEFT JOIN topics t ON t.topic_id = ri.related_topic_id "
                + "WHERE ri.roadmap_id = ? ORDER BY ri.item_order";
        List<RoadmapItem> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, roadmapId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RoadmapItem item = new RoadmapItem();
                    item.setId(rs.getLong("id"));
                    item.setRoadmapId(rs.getLong("roadmap_id"));
                    item.setItemOrder(rs.getInt("item_order"));
                    item.setCategory(RoadmapCategory.valueOf(rs.getString("category")));
                    item.setPriority(RoadmapPriority.valueOf(rs.getString("priority")));
                    item.setTitle(rs.getString("title"));
                    item.setDescription(rs.getString("description"));
                    int relatedTopicId = rs.getInt("related_topic_id");
                    item.setRelatedTopicId(rs.wasNull() ? null : relatedTopicId);
                    item.setRelatedTopicName(rs.getString("topic_name"));
                    item.setStatus(RoadmapItemStatus.valueOf(rs.getString("status")));
                    Timestamp completedAt = rs.getTimestamp("completed_at");
                    if (completedAt != null) item.setCompletedAt(completedAt.toLocalDateTime());
                    result.add(item);
                }
            }
        }
        return result;
    }

    private LearningRoadmap mapRoadmapRow(ResultSet rs) throws SQLException {
        LearningRoadmap roadmap = new LearningRoadmap();
        roadmap.setRoadmapId(rs.getLong("roadmap_id"));
        roadmap.setUserId(rs.getLong("user_id"));
        double score = rs.getDouble("readiness_score");
        roadmap.setReadinessScore(rs.wasNull() ? null : score);
        roadmap.setReadinessBand(rs.getString("readiness_band"));
        roadmap.setSummary(rs.getString("summary"));
        Timestamp generatedAt = rs.getTimestamp("generated_at");
        if (generatedAt != null) roadmap.setGeneratedAt(generatedAt.toLocalDateTime());
        return roadmap;
    }
}
