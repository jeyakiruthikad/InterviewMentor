package com.careerintelligence.service;

import com.careerintelligence.dao.CompanyProfileDAO;
import com.careerintelligence.dao.RolePrepQuestionDAO;
import com.careerintelligence.model.CompanyProfile;
import com.careerintelligence.model.RolePrepQuestion;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Business logic for role and company interview preparation:
 * role/company-specific interview questions, company reference info, and
 * skill-gap analysis for a target role. Skill-gap analysis itself is
 * already implemented by {@link ResumeService#getMissingSkills} (vs.
 * `role_skill_requirements`, an current implementation table) - this service reuses
 * it rather than duplicating it, and adds the question bank + company
 * profile lookups on top.
 */
public class RolePrepService {

    private final RolePrepQuestionDAO rolePrepQuestionDAO = new RolePrepQuestionDAO();
    private final CompanyProfileDAO companyProfileDAO = new CompanyProfileDAO();
    private final ResumeService resumeService = new ResumeService();

    /** Full bundle for the "Role & Company Preparation" screen for one user + chosen role/company. */
    public record PrepBundle(String roleName, String companyName, List<String> missingSkills,
                              Optional<CompanyProfile> companyProfile, List<RolePrepQuestion> questions) {
    }

    public PrepBundle buildPrepBundle(Long userId, String roleName, String companyName) throws SQLException {
        List<String> missingSkills = resumeService.getMissingSkills(userId);
        Optional<CompanyProfile> companyProfile = companyProfileDAO.findByName(companyName);
        List<RolePrepQuestion> questions = rolePrepQuestionDAO.findForRoleAndCompany(roleName, companyName);
        return new PrepBundle(roleName, companyName, missingSkills, companyProfile, questions);
    }

    public List<String> getAvailableTargetRoles() throws SQLException {
        return resumeService.getAvailableTargetRoles();
    }

    public List<String> getRoleNamesWithPrepQuestions() throws SQLException {
        return rolePrepQuestionDAO.findDistinctRoleNames();
    }

    public List<CompanyProfile> getAllCompanies() throws SQLException {
        return companyProfileDAO.findAllActive();
    }

    // -------------------------------------------------------------
    // Admin Module: role/company question + company profile CRUD
    // -------------------------------------------------------------

    public List<RolePrepQuestion> adminFindAllQuestions() throws SQLException {
        return rolePrepQuestionDAO.findAll();
    }

    public RolePrepQuestion adminCreateQuestion(RolePrepQuestion question) throws SQLException {
        return rolePrepQuestionDAO.create(question);
    }

    public boolean adminUpdateQuestion(RolePrepQuestion question) throws SQLException {
        return rolePrepQuestionDAO.update(question);
    }

    public boolean adminDeleteQuestion(long id) throws SQLException {
        return rolePrepQuestionDAO.delete(id);
    }

    public List<CompanyProfile> adminFindAllCompanies() throws SQLException {
        return companyProfileDAO.findAll();
    }

    public CompanyProfile adminSaveCompany(CompanyProfile profile) throws SQLException {
        return companyProfileDAO.save(profile);
    }

    public boolean adminDeleteCompany(int companyId) throws SQLException {
        return companyProfileDAO.delete(companyId);
    }
}
