package com.careerintelligence.model;

/** Represents a row in `badge_catalog`: the reference definition of an awardable badge. */
public class Badge {

    private String badgeCode;
    private String badgeName;
    private String description;
    private String criteria;

    public Badge() {
    }

    public Badge(String badgeCode, String badgeName, String description, String criteria) {
        this.badgeCode = badgeCode;
        this.badgeName = badgeName;
        this.description = description;
        this.criteria = criteria;
    }

    public String getBadgeCode() {
        return badgeCode;
    }

    public void setBadgeCode(String badgeCode) {
        this.badgeCode = badgeCode;
    }

    public String getBadgeName() {
        return badgeName;
    }

    public void setBadgeName(String badgeName) {
        this.badgeName = badgeName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCriteria() {
        return criteria;
    }

    public void setCriteria(String criteria) {
        this.criteria = criteria;
    }
}
