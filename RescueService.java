package oracletest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Rescue / trap workflow and rescue teams (tables RESCUE_CASE, RESCUE_TEAM).
 *
 * Stages : Reported -> Assigned -> On the way -> Trapped / Rescued -> Closed
 * Outcome (when closing an animal case): Relocated, Released, Treated,
 *          Handed to a vet.
 *
 * Response time is measured from REPORTED_AT to ON_WAY_AT.
 */
final class RescueService {

    private RescueService() { }

    // ---- codes and labels ---------------------------------------------

    static final String[] STAGES =
            { "REPORTED", "ASSIGNED", "ON_THE_WAY", "SECURED", "CLOSED" };
    static final String[] STAGE_LABELS =
            { "Reported", "Assigned", "On the way", "Trapped / Rescued", "Closed" };

    static final String[] OUTCOMES =
            { "RELOCATED", "RELEASED", "TREATED", "HANDED_TO_VET" };
    static final String[] OUTCOME_LABELS =
            { "Relocated", "Released", "Treated", "Handed to a vet" };

    static final String[] SKILLS = { "SNAKE_HANDLING", "LARGE_ANIMAL",
            "VETERINARY", "GENERAL", "ANIMAL_WELFARE" };
    static final String[] SKILL_LABELS = { "Snake handling", "Large animals",
            "Veterinary", "General", "Animal welfare / vet (strays)" };

    static final String TYPE_ANIMAL = "ANIMAL";
    static final String TYPE_HUMAN = "HUMAN_SAFETY";

    private static final String[] SNAKE_WORDS =
            { "SNAKE", "COBRA", "PYTHON", "VIPER", "KRAIT", "RAT SNAKE" };
    private static final String[] LARGE_WORDS = { "ELEPHANT", "TIGER", "LEOPARD",
            "LION", "BEAR", "BISON", "GAUR", "RHINO", "PANTHER", "HYENA" };
    private static final String[] DOMESTIC_WORDS =
            { "DOMESTIC", "STRAY", "PET", "LIVESTOCK" };
    private static final String[] INJURED_WORDS =
            { "INJUR", "SICK", "WOUND", "HURT", "TRAPPED", "DISEASE" };

    static String label(String[] codes, String[] labels, String code) {
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(code)) {
                return labels[i];
            }
        }
        return code == null ? "" : code;
    }

    static String stageLabel(String code) {
        return label(STAGES, STAGE_LABELS, code);
    }

    static String priorityLabel(int p) {
        return p == 1 ? "URGENT" : p == 2 ? "HIGH" : "NORMAL";
    }

    private static boolean has(String text, String[] words) {
        String t = text == null ? "" : text.toUpperCase();
        for (String w : words) {
            if (t.contains(w)) {
                return true;
            }
        }
        return false;
    }

    // ---- priority and skill -------------------------------------------

    /** 1 = urgent, 2 = high, 3 = normal. Rare and injured animals rank higher. */
    static int priorityFor(boolean humanSafety, boolean rare, boolean injured,
                           int riskRank) {
        if (humanSafety) {
            return 1;
        }
        int points = (rare ? 2 : 0) + (injured ? 2 : 0)
                + (riskRank >= 3 ? 2 : riskRank == 2 ? 1 : 0);
        return points >= 4 ? 1 : points >= 2 ? 2 : 3;
    }

    static String skillFor(String species, String category, boolean injured) {
        if (has(category, DOMESTIC_WORDS)) {
            return "ANIMAL_WELFARE";
        }
        if (has(species, SNAKE_WORDS)) {
            return "SNAKE_HANDLING";
        }
        if (has(species, LARGE_WORDS)) {
            return "LARGE_ANIMAL";
        }
        return injured ? "VETERINARY" : "GENERAL";
    }

    // ---- transactions ---------------------------------------------------

    private interface Work {
        void run() throws SQLException;
    }

    private static void inTransaction(Connection con, Work work)
            throws SQLException {
        boolean old = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            work.run();
            con.commit();
        } catch (SQLException | RuntimeException ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(old);
        }
    }

    private static int update(Connection con, String sql, Object... params)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                if (params[i] == null) {
                    ps.setNull(i + 1, Types.VARCHAR);
                } else {
                    ps.setObject(i + 1, params[i]);
                }
            }
            return ps.executeUpdate();
        }
    }

    // ---- opening cases --------------------------------------------------

    /**
     * Opens a case for every sighting that needs rescue and has none yet,
     * then gives waiting cases to free teams. Returns how many were opened.
     */
    static int syncCases(Connection con) throws SQLException {
        // an officer marked the sighting Solved: its open case is finished too
        List<Integer> solved = new ArrayList<Integer>();
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT c.CASE_ID FROM RESCUE_CASE c JOIN ANIMAL_SIGHTING s "
                     + "ON s.SIGHTING_ID = c.SIGHTING_ID WHERE c.STAGE <> 'CLOSED' "
                     + "AND UPPER(NVL(s.VERIFICATION_STATUS, '')) LIKE 'SOLV%'")) {
            while (rs.next()) {
                solved.add(rs.getInt(1));
            }
        }
        for (int caseId : solved) {
            close(con, caseId, null, 0);
        }

        List<String> ids = new ArrayList<String>();
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT SIGHTING_ID FROM ANIMAL_SIGHTING s "
                     + "WHERE s.NEEDS_RESCUE = 1 "
                     + "AND UPPER(NVL(s.VERIFICATION_STATUS, 'PENDING')) NOT LIKE 'SOLV%' "
                     + "AND NOT EXISTS (SELECT 1 FROM RESCUE_CASE c "
                     + "WHERE c.SIGHTING_ID = s.SIGHTING_ID)")) {
            while (rs.next()) {
                ids.add(rs.getString(1));
            }
        }
        for (String id : ids) {
            openFromSighting(con, id);
        }
        autoAssignWaiting(con);
        return ids.size();
    }

    /** Opens a case for one sighting (does nothing if it already has one). */
    static boolean openFromSighting(Connection con, String sightingId)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM RESCUE_CASE WHERE SIGHTING_ID = ?")) {
            ps.setString(1, sightingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return false;
                }
            }
        }

        String sql = "SELECT s.SPECIES_NAME, s.AREA_NAME, s.CITY, s.DISTRICT, "
                + "s.ANIMAL_CONDITION, s.ANIMAL_CATEGORY, s.IS_RARE, "
                + "s.CONTACT_PHONE, s.REPORTED_BY, "
                + "(SELECT r.RISK_LEVEL FROM RISK_RECORD r "
                + " WHERE r.SIGHTING_ID = s.SIGHTING_ID FETCH FIRST 1 ROWS ONLY) AS LVL "
                + "FROM ANIMAL_SIGHTING s WHERE s.SIGHTING_ID = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, sightingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                String species = rs.getString("SPECIES_NAME");
                boolean injured = has(rs.getString("ANIMAL_CONDITION"), INJURED_WORDS);
                boolean rare = "1".equals(rs.getString("IS_RARE"));
                int risk = AlertRouting.rank(rs.getString("LVL"));
                int priority = priorityFor(false, rare, injured, risk);
                String skill = skillFor(species, rs.getString("ANIMAL_CATEGORY"), injured);

                update(con, "INSERT INTO RESCUE_CASE (SIGHTING_ID, CASE_TYPE, STAGE, "
                        + "PRIORITY, NEEDED_SKILL, SPECIES_NAME, AREA_NAME, CITY, "
                        + "DISTRICT, CONTACT_PHONE, REPORTED_BY) "
                        + "VALUES (?, 'ANIMAL', 'REPORTED', ?, ?, ?, ?, ?, ?, ?, ?)",
                        sightingId, priority, skill, species,
                        rs.getString("AREA_NAME"), rs.getString("CITY"),
                        rs.getString("DISTRICT"), rs.getString("CONTACT_PHONE"),
                        rs.getString("REPORTED_BY"));
            }
        }
        return true;
    }

    /** SOS from a person in danger. Returns true when a team was assigned. */
    static boolean createSos(Connection con, int userId, String description,
            String phone, String area, String city, String district)
            throws SQLException {
        update(con, "INSERT INTO RESCUE_CASE (SIGHTING_ID, CASE_TYPE, STAGE, "
                + "PRIORITY, NEEDED_SKILL, SPECIES_NAME, AREA_NAME, CITY, DISTRICT, "
                + "DESCRIPTION, CONTACT_PHONE, REPORTED_BY) "
                + "VALUES (NULL, 'HUMAN_SAFETY', 'REPORTED', 1, 'GENERAL', "
                + "'Human safety (SOS)', ?, ?, ?, ?, ?, ?)",
                area, city, district, description, phone, userId);
        int before = countOpenUnassigned(con);
        autoAssignWaiting(con);
        return countOpenUnassigned(con) < before;
    }

    private static int countOpenUnassigned(Connection con) throws SQLException {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM RESCUE_CASE "
                     + "WHERE STAGE = 'REPORTED' AND TEAM_ID IS NULL")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Open SOS cases that no team has taken yet (officers get a popup). */
    static List<String[]> openSos(Connection con) throws SQLException {
        List<String[]> list = new ArrayList<String[]>();
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT CASE_ID, AREA_NAME, DESCRIPTION, "
                     + "CONTACT_PHONE FROM RESCUE_CASE WHERE CASE_TYPE = 'HUMAN_SAFETY' "
                     + "AND STAGE <> 'CLOSED' ORDER BY REPORTED_AT DESC")) {
            while (rs.next()) {
                list.add(new String[] { rs.getString(1), rs.getString(2),
                        rs.getString(3), rs.getString(4) });
            }
        }
        return list;
    }

    // ---- assignment -----------------------------------------------------

    /** Free team with the right skill in the case's area (General as backup). */
    private static Integer findTeam(Connection con, String area, String skill)
            throws SQLException {
        String[] tries = skill.equals("GENERAL")
                ? new String[] { "GENERAL" } : new String[] { skill, "GENERAL" };
        for (String s : tries) {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT TEAM_ID FROM RESCUE_TEAM WHERE AVAILABLE = 1 "
                    + "AND UPPER(TRIM(AREA)) = UPPER(TRIM(?)) AND SKILL = ? "
                    + "ORDER BY TEAM_ID FETCH FIRST 1 ROWS ONLY")) {
                ps.setString(1, area == null ? "" : area);
                ps.setString(2, s);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        return null;
    }

    /** Most urgent waiting cases get the free teams first. */
    static void autoAssignWaiting(Connection con) throws SQLException {
        List<Object[]> waiting = new ArrayList<Object[]>();
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT CASE_ID, AREA_NAME, NEEDED_SKILL "
                     + "FROM RESCUE_CASE WHERE STAGE = 'REPORTED' AND TEAM_ID IS NULL "
                     + "ORDER BY PRIORITY, REPORTED_AT")) {
            while (rs.next()) {
                waiting.add(new Object[] { rs.getInt(1), rs.getString(2),
                        rs.getString(3) });
            }
        }
        for (Object[] w : waiting) {
            Integer team = findTeam(con, (String) w[1],
                    w[2] == null ? "GENERAL" : (String) w[2]);
            if (team != null) {
                assign(con, (Integer) w[0], team);
            }
        }
    }

    /** Gives the case to a team (frees the previous team, if any). */
    static void assign(Connection con, int caseId, int teamId)
            throws SQLException {
        inTransaction(con, () -> {
            update(con, "UPDATE RESCUE_TEAM SET AVAILABLE = 1 WHERE TEAM_ID = "
                    + "(SELECT TEAM_ID FROM RESCUE_CASE WHERE CASE_ID = ?)", caseId);
            update(con, "UPDATE RESCUE_CASE SET TEAM_ID = ?, STAGE = 'ASSIGNED', "
                    + "ASSIGNED_AT = SYSDATE WHERE CASE_ID = ?", teamId, caseId);
            update(con, "UPDATE RESCUE_TEAM SET AVAILABLE = 0 WHERE TEAM_ID = ?",
                    teamId);
        });
    }

    // ---- stage changes --------------------------------------------------

    /** ON_THE_WAY or SECURED. (Closing goes through close().) */
    static void advance(Connection con, int caseId, String stage)
            throws SQLException {
        String column = stage.equals("ON_THE_WAY") ? "ON_WAY_AT" : "SECURED_AT";
        update(con, "UPDATE RESCUE_CASE SET STAGE = ?, " + column
                + " = NVL(" + column + ", SYSDATE) WHERE CASE_ID = ? "
                + "AND STAGE <> 'CLOSED'", stage, caseId);
    }

    /**
     * Closes the case, frees its team, marks the sighting Solved and resolves
     * its public alert (people in range then see an "Area clear" notice).
     * outcome is null for a human safety case.
     */
    static void close(Connection con, int caseId, String outcome, int officerId)
            throws SQLException {
        inTransaction(con, () -> {
            String sightingId = null;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT SIGHTING_ID FROM RESCUE_CASE WHERE CASE_ID = ?")) {
                ps.setInt(1, caseId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        sightingId = rs.getString(1);
                    }
                }
            }
            update(con, "UPDATE RESCUE_TEAM SET AVAILABLE = 1 WHERE TEAM_ID = "
                    + "(SELECT TEAM_ID FROM RESCUE_CASE WHERE CASE_ID = ?)", caseId);
            update(con, "UPDATE RESCUE_CASE SET STAGE = 'CLOSED', OUTCOME = ?, "
                    + "CLOSED_AT = SYSDATE WHERE CASE_ID = ?", outcome, caseId);
            if (sightingId != null) {
                // keep the officer's own status update if it is already Solved
                update(con, "UPDATE ANIMAL_SIGHTING SET VERIFICATION_STATUS = 'SOLVED', "
                        + "STATUS_UPDATED_BY = ?, STATUS_UPDATED_DATE = SYSDATE "
                        + "WHERE SIGHTING_ID = ? "
                        + "AND UPPER(NVL(VERIFICATION_STATUS, '')) NOT LIKE 'SOLV%'",
                        officerId, sightingId);
                update(con, "UPDATE RISK_ALERT SET STATUS = 'Resolved' WHERE RISK_ID IN "
                        + "(SELECT RISK_ID FROM RISK_RECORD WHERE SIGHTING_ID = ?)",
                        sightingId);
            }
        });
        autoAssignWaiting(con);    // the freed team may take a waiting case
    }

    /** Open (not closed) rescue cases of one sighting. */
    static List<Integer> openCasesOf(Connection con, String sightingId)
            throws SQLException {
        List<Integer> ids = new ArrayList<Integer>();
        try (PreparedStatement ps = con.prepareStatement("SELECT CASE_ID FROM "
                + "RESCUE_CASE WHERE SIGHTING_ID = ? AND STAGE <> 'CLOSED'")) {
            ps.setString(1, sightingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    /** Closes the open case(s) of a sighting an officer has just marked Solved. */
    static void closeBySighting(Connection con, String sightingId, String outcome,
            int officerId) throws SQLException {
        for (int caseId : openCasesOf(con, sightingId)) {
            close(con, caseId, outcome, officerId);
        }
    }

    // ---- teams ------------------------------------------------------------

    static void addTeam(Connection con, String name, String phone, String area,
            String skill, boolean available) throws SQLException {
        update(con, "INSERT INTO RESCUE_TEAM (TEAM_NAME, PHONE, AREA, SKILL, AVAILABLE) "
                + "VALUES (?, ?, ?, ?, ?)", name, phone, area, skill,
                available ? 1 : 0);
        autoAssignWaiting(con);
    }

    static void updateTeam(Connection con, int teamId, String name, String phone,
            String area, String skill, boolean available) throws SQLException {
        update(con, "UPDATE RESCUE_TEAM SET TEAM_NAME = ?, PHONE = ?, AREA = ?, "
                + "SKILL = ?, AVAILABLE = ? WHERE TEAM_ID = ?",
                name, phone, area, skill, available ? 1 : 0, teamId);
        autoAssignWaiting(con);
    }

    /** Returns false (and deletes nothing) if the team still has open cases. */
    static boolean deleteTeam(Connection con, int teamId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM "
                + "RESCUE_CASE WHERE TEAM_ID = ? AND STAGE <> 'CLOSED'")) {
            ps.setInt(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return false;
                }
            }
        }
        // closed cases keep their history but lose the link to the team
        update(con, "UPDATE RESCUE_CASE SET TEAM_ID = NULL WHERE TEAM_ID = ?", teamId);
        update(con, "DELETE FROM RESCUE_TEAM WHERE TEAM_ID = ?", teamId);
        return true;
    }

    // ---- rescue report --------------------------------------------------

    private static String one(Connection con, String sql) throws SQLException {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next() && rs.getString(1) != null) {
                return rs.getString(1);
            }
        }
        return "0";
    }

    /** { short summary, full report text } */
    static String[] report(Connection con) throws SQLException {
        StringBuilder sb = new StringBuilder();
        String total = one(con, "SELECT COUNT(*) FROM RESCUE_CASE");
        String closed = one(con,
                "SELECT COUNT(*) FROM RESCUE_CASE WHERE STAGE = 'CLOSED'");
        String open = one(con,
                "SELECT COUNT(*) FROM RESCUE_CASE WHERE STAGE <> 'CLOSED'");
        String sos = one(con,
                "SELECT COUNT(*) FROM RESCUE_CASE WHERE CASE_TYPE = 'HUMAN_SAFETY'");
        String avgResp = one(con, "SELECT ROUND(AVG((ON_WAY_AT - REPORTED_AT) * 1440), 1) "
                + "FROM RESCUE_CASE WHERE ON_WAY_AT IS NOT NULL");
        String avgClose = one(con, "SELECT ROUND(AVG((CLOSED_AT - REPORTED_AT) * 1440), 1) "
                + "FROM RESCUE_CASE WHERE CLOSED_AT IS NOT NULL");

        sb.append("Cases handled (closed) : ").append(closed).append("\n");
        sb.append("Cases still open       : ").append(open).append("\n");
        sb.append("Total cases            : ").append(total).append("\n");
        sb.append("Human safety (SOS)     : ").append(sos).append("\n");
        sb.append("Avg response time      : ").append(avgResp)
                .append(" min  (reported -> team on the way)\n");
        sb.append("Avg time to close      : ").append(avgClose).append(" min\n\n");

        sb.append("OUTCOMES BY SPECIES\n");
        sb.append(String.format("  %-22s %-16s %s%n", "Species", "Outcome", "Cases"));
        boolean any = false;
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT NVL(SPECIES_NAME, '-'), OUTCOME, "
                     + "COUNT(*) FROM RESCUE_CASE WHERE STAGE = 'CLOSED' "
                     + "AND OUTCOME IS NOT NULL GROUP BY SPECIES_NAME, OUTCOME "
                     + "ORDER BY SPECIES_NAME, COUNT(*) DESC")) {
            while (rs.next()) {
                any = true;
                sb.append(String.format("  %-22s %-16s %d%n", cut(rs.getString(1), 22),
                        label(OUTCOMES, OUTCOME_LABELS, rs.getString(2)), rs.getInt(3)));
            }
        }
        if (!any) {
            sb.append("  (no closed animal cases yet)\n");
        }

        sb.append("\nBY TEAM\n");
        sb.append(String.format("  %-22s %-8s %s%n", "Team", "Handled", "Avg response (min)"));
        any = false;
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT t.TEAM_NAME, "
                     + "SUM(CASE WHEN c.STAGE = 'CLOSED' THEN 1 ELSE 0 END), "
                     + "ROUND(AVG((c.ON_WAY_AT - c.REPORTED_AT) * 1440), 1) "
                     + "FROM RESCUE_TEAM t JOIN RESCUE_CASE c ON c.TEAM_ID = t.TEAM_ID "
                     + "GROUP BY t.TEAM_NAME ORDER BY 2 DESC")) {
            while (rs.next()) {
                any = true;
                String avg = rs.getString(3) == null ? "-" : rs.getString(3);
                sb.append(String.format("  %-22s %-8d %s%n",
                        cut(rs.getString(1), 22), rs.getInt(2), avg));
            }
        }
        if (!any) {
            sb.append("  (no team has taken a case yet)\n");
        }

        String shortText = closed + " closed, avg " + avgResp + " min";
        return new String[] { shortText, sb.toString() };
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}