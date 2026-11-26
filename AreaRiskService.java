package oracletest;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * AreaRiskService - area history, risk scoring, ROUTING of a new sighting,
 * RISK_RECORD / RISK_ALERT saving, SOS and the warning popup.
 *
 * WHO GETS TOLD (routing of a new sighting)
 *   Domestic animal         -> forest officer only (no risk record, no alert)
 *   Wild animal, LOW risk   -> forest officer only (risk record, no public alert)
 *   Wild animal, MEDIUM/HIGH-> public alert (shown by area / city / district)
 *                              + a human-safety trap / rescue case
 *   Any wild animal that needs rescue -> animal rescue case
 *   Rare species            -> name hidden from the public (shown as
 *                              "Protected wild animal"), officers see everything
 *   Alert from a general user is "UNVERIFIED" until an officer confirms it.
 */
public class AreaRiskService {

    private static final int LOOKBACK_DAYS = 180;
    private static final int HIGH_SCORE = 8;
    private static final int MEDIUM_SCORE = 4;

    public static final String PROTECTED_NAME = "Protected wild animal";

    private static final Color RISK_HIGH = new Color(204, 76, 76);
    private static final Color RISK_MEDIUM = new Color(235, 174, 72);
    private static final Color RISK_LOW = new Color(76, 155, 105);
    private static final Color DARK_GREEN = new Color(24, 75, 53);
    private static final Color GREEN = new Color(53, 125, 85);
    private static final Color TEXT = new Color(45, 55, 48);
    private static final Color MUTED = new Color(115, 125, 117);
    private static final Color BORDER = new Color(225, 232, 225);

    private static final Set<String> DANGEROUS = new HashSet<String>(Arrays.asList(
            "tiger", "leopard", "lion", "bear", "elephant", "asian elephant",
            "crocodile"));

    private static final Set<String> MODERATE = new HashSet<String>(Arrays.asList(
            "wild pig", "wild boar", "monkey", "snake", "sanke",
            "indian rock python", "bengal monitor", "fishing cat",
            "nilgiri thar", "spotted deer", "indian fox", "indian porcupine",
            "smooth-coated otter", "asian small-clawed otter", "others"));

    private static final Set<String> DOMESTIC = new HashSet<String>(Arrays.asList(
            "cow", "goat", "dog", "cat", "sheep", "horse", "donkey", "chicken",
            "camel", "rabbit", "duck", "goose", "fishes", "water buffalo",
            "domestic pig"));

    // -----------------------------------------------------------------
    // DATA CLASSES
    // -----------------------------------------------------------------

    public static class SpeciesHistory {
        public String species;
        public int count;
        public int conflicts;
        public Timestamp lastSeen;
        public boolean rare;
    }

    public static class AreaHistory {
        public String area;
        public String city;
        public String district;
        public boolean hideRare;       // true for the public: rare names are masked
        public boolean latestRare;
        public List<SpeciesHistory> items = new ArrayList<SpeciesHistory>();
        public int totalSightings;
        public int totalConflicts;
        public int latestSightingId;   // newest sighting in the area (0 = none)

        public String nameOf(SpeciesHistory s) {
            return (hideRare && s.rare) ? PROTECTED_NAME : s.species;
        }
    }

    public static class Assessment {
        public String level;
        public int score;
        public List<String> reasons = new ArrayList<String>();
    }

    /** Everything the form knows about the sighting that was just saved. */
    public static class SightingInfo {
        public int sightingId;
        public int reporterId;
        public String reporterRole = "";
        public String category = "Wild";
        public String species = "";
        public String area = "";
        public String city = "";
        public String district = "";
        public double distance = Double.NaN;
        public boolean conflict;
        public boolean rare;
        public boolean needsRescue;
        public String condition;
        public String phone;
        public String details;
    }

    private static class AlertSave {
        int sightingId;
        String area;
        String city;
        String district;
        String species;
        double distance = Double.NaN;
        int conflictFrequency;
        Assessment assessment;
        boolean createAlert;
        boolean verified;
        boolean rare;
    }

    // -----------------------------------------------------------------
    // SMALL HELPERS
    // -----------------------------------------------------------------

    /** Trim and collapse repeated spaces (capital letters are kept). */
    public static String tidy(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    public static boolean isManagerRole(String role) {
        return "Admin".equalsIgnoreCase(role) || "Forest Officer".equalsIgnoreCase(role);
    }

    public static boolean isDomestic(String category, String species) {
        if (category != null && category.trim().equalsIgnoreCase("Domestic")) {
            return true;
        }
        if (category != null && category.trim().equalsIgnoreCase("Wild")) {
            return false;
        }
        return species != null && DOMESTIC.contains(species.trim().toLowerCase());
    }

    public static boolean isSnake(String species) {
        String s = species == null ? "" : species.trim().toLowerCase();
        return s.contains("snake") || s.contains("sanke") || s.contains("python")
                || s.contains("cobra") || s.contains("viper") || s.contains("krait");
    }

    public static int speciesPoints(String species) {
        String s = species == null ? "" : species.trim().toLowerCase();
        if (DOMESTIC.contains(s)) {
            return 0;
        }
        if (DANGEROUS.contains(s) || s.contains("elephant")) {
            return 4;
        }
        if (MODERATE.contains(s)) {
            return 2;
        }
        return 1;
    }

    // -----------------------------------------------------------------
    // 1) AREA HISTORY  (wild animals only)
    // -----------------------------------------------------------------

    public static AreaHistory loadHistory(String area) throws SQLException {
        return loadHistory(area, false);
    }

    /** @param hideRare true for the public: rare species are shown as "Protected wild animal" */
    public static AreaHistory loadHistory(String area, boolean hideRare)
            throws SQLException {
        AreaHistory history = new AreaHistory();
        history.area = tidy(area);
        history.hideRare = hideRare;

        String sql =
                "SELECT SPECIES_NAME, COUNT(*) AS TOTAL, "
                + "SUM(CASE WHEN HAS_CONFLICT = 1 THEN 1 ELSE 0 END) AS CONFLICTS, "
                + "MAX(SIGHTING_DATE) AS LAST_SEEN, MAX(SIGHTING_ID) AS LAST_ID, "
                + "MAX(NVL(IS_RARE, 0)) AS RARE_FLAG "
                + "FROM ANIMAL_SIGHTING "
                + "WHERE REGEXP_REPLACE(UPPER(TRIM(AREA_NAME)), '\\s+', ' ') = ? "
                + "AND ANIMAL_CATEGORY = 'Wild' "
                + "AND SIGHTING_DATE >= SYSDATE - " + LOOKBACK_DAYS + " "
                + "GROUP BY SPECIES_NAME "
                + "ORDER BY TOTAL DESC";

        try (Connection con = DBConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, history.area.toUpperCase());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        SpeciesHistory item = new SpeciesHistory();
                        item.species = rs.getString("SPECIES_NAME");
                        item.count = rs.getInt("TOTAL");
                        item.conflicts = rs.getInt("CONFLICTS");
                        item.lastSeen = rs.getTimestamp("LAST_SEEN");
                        item.rare = rs.getInt("RARE_FLAG") == 1;
                        history.items.add(item);
                        history.totalSightings += item.count;
                        history.totalConflicts += item.conflicts;
                        history.latestSightingId = Math.max(
                                history.latestSightingId, rs.getInt("LAST_ID"));
                    }
                }
            }
            if (history.latestSightingId > 0) {
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT CITY, DISTRICT, NVL(IS_RARE, 0) FROM ANIMAL_SIGHTING "
                        + "WHERE SIGHTING_ID = ?")) {
                    ps.setInt(1, history.latestSightingId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            history.city = rs.getString(1);
                            history.district = rs.getString(2);
                            history.latestRare = rs.getInt(3) == 1;
                        }
                    }
                }
            }
        }
        return history;
    }

    // -----------------------------------------------------------------
    // 2) RISK ASSESSMENT
    // -----------------------------------------------------------------

    public static Assessment assess(AreaHistory h, String species,
                                    double distance, boolean hasConflict) {
        Assessment a = new Assessment();
        int score = 0;

        if (species != null && !species.trim().isEmpty()) {
            int p = speciesPoints(species);
            score += p;
            if (p >= 4) {
                a.reasons.add(species + " is a dangerous animal (+" + p + ")");
            } else if (p >= 2) {
                a.reasons.add(species + " can cause damage (+" + p + ")");
            }
        } else {
            int max = 0;
            String worst = null;
            for (SpeciesHistory s : h.items) {
                int p = speciesPoints(s.species);
                if (p > max) {
                    max = p;
                    worst = h.nameOf(s);
                }
            }
            score += max;
            if (worst != null && max >= 2) {
                a.reasons.add("Most dangerous animal seen here: " + worst
                        + " (+" + max + ")");
            }
        }

        int n = h.totalSightings;
        int busy = n == 0 ? 0 : n <= 2 ? 1 : n <= 5 ? 2 : 3;
        if (busy > 0) {
            score += busy;
            a.reasons.add(n + " earlier sighting(s) in this area in the last "
                    + LOOKBACK_DAYS + " days (+" + busy + ")");
        }

        int c = h.totalConflicts;
        int conflictPts = c == 0 ? 0 : c <= 2 ? 2 : 3;
        if (conflictPts > 0) {
            score += conflictPts;
            a.reasons.add(c + " earlier human-animal conflict(s) here (+"
                    + conflictPts + ")");
        }

        if (species != null) {
            for (SpeciesHistory s : h.items) {
                if (s.species != null && s.species.equalsIgnoreCase(species.trim())) {
                    score += 1;
                    a.reasons.add(species + " was seen here before (+1)");
                    break;
                }
            }
        }

        if (!Double.isNaN(distance)) {
            if (distance <= 1) {
                score += 2;
                a.reasons.add("Animal is very close, " + distance + " km (+2)");
            } else if (distance <= 3) {
                score += 1;
                a.reasons.add("Animal is nearby, " + distance + " km (+1)");
            }
            // a snake right next to people is more dangerous than one far away
            if (species != null && isSnake(species) && distance <= 0.5) {
                score += 2;
                a.reasons.add("Snake very close to people (+2)");
            }
        }

        if (hasConflict) {
            score += 2;
            a.reasons.add("A conflict happened in this report (+2)");
        }

        a.score = score;
        a.level = score >= HIGH_SCORE ? "HIGH"
                : score >= MEDIUM_SCORE ? "MEDIUM" : "LOW";
        if (a.reasons.isEmpty()) {
            a.reasons.add("No earlier records found for this area.");
        }
        return a;
    }

    // -----------------------------------------------------------------
    // 3) SAVE RISK_RECORD (+ RISK_ALERT when it is a public alert)
    // -----------------------------------------------------------------

    // DB check constraints expect 'Low' / 'Medium' / 'High'
    public static String dbLevel(String level) {
        if ("HIGH".equals(level)) {
            return "High";
        } else if ("MEDIUM".equals(level)) {
            return "Medium";
        }
        return "Low";
    }

    // ALERT_MESSAGE column is only VARCHAR2(25)
    public static String alertMessage(String level) {
        if ("HIGH".equals(level)) {
            return "HIGH risk - stay indoors";
        } else if ("MEDIUM".equals(level)) {
            return "MEDIUM risk - be careful";
        }
        return "LOW risk - stay aware";
    }

    private static void setNullableText(PreparedStatement ps, int index, String v)
            throws SQLException {
        if (v == null || v.trim().isEmpty()) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, v.trim());
        }
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static void saveRiskAndAlert(AlertSave p) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                insertRiskAndAlert(con, p);
                con.commit();
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            }
        }
    }

    private static void insertRiskAndAlert(Connection con, AlertSave p)
            throws SQLException {
        String riskSql =
                "INSERT INTO RISK_RECORD (SIGHTING_ID, SPECIES_NAME, AREA_NAME, "
                + "CONFLICT_FREQUENCY, DISTANCE, RISK_SCORE, RISK_LEVEL) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        String alertSql =
                "INSERT INTO RISK_ALERT (RISK_ID, AREA_NAME, SPECIES_NAME, "
                + "RISK_SCORE, RISK_LEVEL, ALERT_MESSAGE, ALERT_DATE, STATUS, "
                + "CITY, DISTRICT, VERIFIED, IS_RARE) "
                + "VALUES (?, ?, ?, ?, ?, ?, SYSDATE, 'Active', ?, ?, ?, ?)";

        try (PreparedStatement ps = con.prepareStatement(riskSql)) {
            ps.setInt(1, p.sightingId);
            ps.setString(2, p.species);
            ps.setString(3, p.area);
            ps.setInt(4, p.conflictFrequency);
            if (Double.isNaN(p.distance)) {
                ps.setNull(5, Types.NUMERIC);
            } else {
                ps.setDouble(5, p.distance);
            }
            ps.setDouble(6, p.assessment.score);
            ps.setString(7, dbLevel(p.assessment.level));
            ps.executeUpdate();
        }
        if (!p.createAlert) {
            return;
        }

        int riskId;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT MAX(RISK_ID) FROM RISK_RECORD WHERE SIGHTING_ID = ?")) {
            ps.setInt(1, p.sightingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) == 0) {
                    throw new SQLException(
                            "RISK_ID was not generated. Check that RISK_ID "
                            + "fills itself (see wildlife_setup.sql, step 3).");
                }
                riskId = rs.getInt(1);
            }
        }
        try (PreparedStatement ps = con.prepareStatement(alertSql)) {
            ps.setInt(1, riskId);
            ps.setString(2, p.area);
            ps.setString(3, p.species);
            ps.setDouble(4, p.assessment.score);
            ps.setString(5, dbLevel(p.assessment.level));
            ps.setString(6, alertMessage(p.assessment.level));
            setNullableText(ps, 7, cut(p.city, 25));
            setNullableText(ps, 8, cut(p.district, 25));
            ps.setInt(9, p.verified ? 1 : 0);
            ps.setInt(10, p.rare ? 1 : 0);
            ps.executeUpdate();
        }
    }

    // -----------------------------------------------------------------
    // 4) NEW SIGHTING  (called by AddAnimalSighting after the save)
    // -----------------------------------------------------------------

    public static void handleNewSighting(Window parent, AreaHistory history,
                                         SightingInfo s) {
        if (history == null) {
            history = new AreaHistory();
            history.area = s.area;
        }
        boolean managerReporter = isManagerRole(s.reporterRole);

        // ---- domestic animal: forest officer only
        if (isDomestic(s.category, s.species)) {
            StringBuilder msg = new StringBuilder(
                    "This is a domestic animal, so no public alert is issued.\n"
                    + "The report has been forwarded to the forest officer.");
            if (s.needsRescue && s.sightingId > 0) {
                String line = openCase(parent, s, RescueService.ANIMAL_RESCUE, "LOW");
                if (line != null) {
                    msg.append("\n\n").append(line);
                }
            }
            JOptionPane.showMessageDialog(parent, msg.toString(),
                    "Report Forwarded", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // ---- wild animal
        Assessment a = assess(history, s.species, s.distance, s.conflict);
        boolean publicAlert = !"LOW".equals(a.level);

        if (s.rare) {
            a.reasons.add("NOTE: rare species - its name is hidden from the public");
        }
        if (s.needsRescue) {
            a.reasons.add("NOTE: animal needs rescue - inform the rescue team");
        }

        if (s.sightingId > 0) {
            try {
                AlertSave p = new AlertSave();
                p.sightingId = s.sightingId;
                p.area = s.area;
                p.city = s.city;
                p.district = s.district;
                p.species = s.species;
                p.distance = s.distance;
                p.conflictFrequency = history.totalConflicts + (s.conflict ? 1 : 0);
                p.assessment = a;
                p.createAlert = publicAlert;
                p.verified = managerReporter;
                p.rare = s.rare;
                saveRiskAndAlert(p);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(parent,
                        "The risk alert could not be saved:\n" + ex.getMessage(),
                        "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        // MEDIUM / HIGH -> trap / safety case, otherwise a rescue case if asked for
        String caseType = publicAlert ? RescueService.HUMAN_SAFETY
                : (s.needsRescue ? RescueService.ANIMAL_RESCUE : null);
        String caseLine = null;
        if (caseType != null && s.sightingId > 0) {
            caseLine = openCase(parent, s, caseType, a.level);
        }

        if (publicAlert) {
            List<String> next = new ArrayList<String>();
            if (caseLine != null) {
                next.add(caseLine);
            }
            if (!managerReporter) {
                next.add("Your alert shows as UNVERIFIED until a forest officer "
                        + "confirms it.");
            }
            showDialog(parent, s.area, s.species, a, history, null, next);
        } else {
            StringBuilder msg = new StringBuilder("Risk level: LOW (score " + a.score + ").\n"
                    + "No public alert is needed. The forest officer has been informed.");
            if (s.rare) {
                msg.append("\nThis is a rare species, so the report stays private.");
            }
            if (caseLine != null) {
                msg.append("\n\n").append(caseLine);
            }
            JOptionPane.showMessageDialog(parent, msg.toString(),
                    "Report Forwarded", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // opens a rescue / safety case in its own transaction
    private static String openCase(Window parent, SightingInfo s,
                                   String caseType, String riskLevel) {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                RescueService.NewCase c = new RescueService.NewCase();
                c.sightingId = s.sightingId;
                c.caseType = caseType;
                c.category = s.category;
                c.species = s.species;
                c.area = s.area;
                c.city = s.city;
                c.district = s.district;
                c.riskLevel = riskLevel;
                c.rare = s.rare;
                c.condition = s.condition;
                c.phone = s.phone;
                c.notes = s.details;
                c.createdBy = s.reporterId;
                RescueService.CaseResult r = RescueService.createCase(con, c);
                con.commit();
                return r.describe();
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(parent,
                    "The rescue case could not be created:\n" + ex.getMessage()
                    + "\n(Run wildlife_setup_v2.sql if you have not yet.)",
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    // -----------------------------------------------------------------
    // 5) SOS  (a person in danger - instant HIGH alert + urgent case)
    // -----------------------------------------------------------------

    /** @return a message for the person: what was sent and who was assigned */
    public static String raiseSos(int userId, String role, String species,
                                  String phone, String note) throws SQLException {
        String animal = cut(tidy(species).isEmpty() ? "Unknown animal" : tidy(species), 25);
        String text = tidy(note);

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                String area = "", city = "", district = "";
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT AREA, CITY, DISTRICT FROM USER_ACCOUNT WHERE USER_ID = ?")) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            area = cut(tidy(rs.getString(1)), 25);
                            city = cut(tidy(rs.getString(2)), 25);
                            district = cut(tidy(rs.getString(3)), 25);
                        }
                    }
                }
                if (area.isEmpty()) {
                    throw new SQLException("Your account has no area. Update your "
                            + "profile first so the alert can be sent to your area.");
                }

                String description = cut("SOS: " + text, 200);
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO ANIMAL_SIGHTING (ANIMAL_CATEGORY, SPECIES_NAME, "
                        + "AREA_NAME, SIGHTING_DATE, DISTANCE, VERIFICATION_STATUS, "
                        + "HAS_CONFLICT, CONFLICT_TYPE, CONFLICT_DATE, DESCRIPTION, "
                        + "REPORTED_BY, IS_RARE, NEEDS_RESCUE, CONTACT_PHONE, CITY, DISTRICT) "
                        + "VALUES ('Wild', ?, ?, SYSDATE, 0, 'PENDING', 1, "
                        + "'SOS - human in danger', SYSDATE, ?, ?, 0, 0, ?, ?, ?)")) {
                    ps.setString(1, animal);
                    ps.setString(2, area);
                    ps.setString(3, description);
                    ps.setInt(4, userId);
                    ps.setString(5, cut(phone, 15));
                    setNullableText(ps, 6, city);
                    setNullableText(ps, 7, district);
                    ps.executeUpdate();
                }

                int sightingId = 0;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT MAX(SIGHTING_ID) FROM ANIMAL_SIGHTING WHERE REPORTED_BY = ?")) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            sightingId = rs.getInt(1);
                        }
                    }
                }
                if (sightingId == 0) {
                    throw new SQLException("The SOS report could not be saved.");
                }

                Assessment a = new Assessment();
                a.level = "HIGH";
                a.score = 10;
                a.reasons.add("SOS sent by a person in danger");

                AlertSave p = new AlertSave();
                p.sightingId = sightingId;
                p.area = area;
                p.city = city;
                p.district = district;
                p.species = animal;
                p.distance = 0;
                p.conflictFrequency = 1;
                p.assessment = a;
                p.createAlert = true;
                p.verified = isManagerRole(role);
                p.rare = false;
                insertRiskAndAlert(con, p);

                RescueService.NewCase c = new RescueService.NewCase();
                c.sightingId = sightingId;
                c.caseType = RescueService.HUMAN_SAFETY;
                c.category = "Wild";
                c.species = animal;
                c.area = area;
                c.city = city;
                c.district = district;
                c.riskLevel = "HIGH";
                c.phone = phone;
                c.notes = cut(text, 100);
                c.createdBy = userId;
                RescueService.CaseResult r = RescueService.createCase(con, c);

                con.commit();
                return "SOS sent. A HIGH risk alert was issued for " + area + ".\n\n"
                        + r.describe();
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            }
        }
    }

    // -----------------------------------------------------------------
    // 6) AREA RISK CHECK  (dashboard menu)
    // -----------------------------------------------------------------

    /** Called by the dashboard "Area Risk Check" menu item. */
    public static void showAreaCheck(Window parent, AreaHistory history,
                                     boolean canIssue, Runnable afterIssue) {
        if (history.items.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "No wild animal sightings were recorded in \"" + history.area
                    + "\" in the last " + LOOKBACK_DAYS + " days.",
                    "Area Risk Check", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        final Assessment a = assess(history, null, Double.NaN, false);
        final String topSpecies = history.items.get(0).species;
        final String area = history.area;
        final int sightingId = history.latestSightingId;
        final int conflicts = history.totalConflicts;
        final String city = history.city;
        final String district = history.district;
        final boolean rare = history.latestRare;

        Runnable issue = null;
        if (canIssue && sightingId > 0) {
            issue = () -> {
                try {
                    AlertSave p = new AlertSave();
                    p.sightingId = sightingId;
                    p.area = area;
                    p.city = city;
                    p.district = district;
                    p.species = topSpecies;
                    p.conflictFrequency = conflicts;
                    p.assessment = a;
                    p.createAlert = true;
                    p.verified = true;       // issued by an officer
                    p.rare = rare;
                    saveRiskAndAlert(p);
                    JOptionPane.showMessageDialog(parent,
                            a.level + " risk alert issued for " + area + ".",
                            "Alert Issued", JOptionPane.INFORMATION_MESSAGE);
                    if (afterIssue != null) {
                        afterIssue.run();
                    }
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(parent,
                            "Unable to issue the alert:\n" + ex.getMessage(),
                            "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            };
        }
        showDialog(parent, area, null, a, history, issue, new ArrayList<String>());
    }

    // -----------------------------------------------------------------
    // 7) THE WARNING POPUP
    // -----------------------------------------------------------------

    private static Color levelColor(String level) {
        if ("HIGH".equals(level)) {
            return RISK_HIGH;
        } else if ("MEDIUM".equals(level)) {
            return RISK_MEDIUM;
        }
        return RISK_LOW;
    }

    private static class WarningIcon extends JComponent {
        private final Color accent;

        WarningIcon(Color accent) {
            this.accent = accent;
            setPreferredSize(new Dimension(90, 90));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int pad = 10;
            int[] xs = { w / 2, w - pad, pad };
            int[] ys = { pad + 2, h - pad - 4, h - pad - 4 };
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(12f,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawPolygon(xs, ys, 3);
            g2.fillPolygon(xs, ys, 3);
            g2.setColor(accent);
            int cx = w / 2;
            g2.fillRoundRect(cx - 5, (int) (h * 0.36), 10, (int) (h * 0.26), 8, 8);
            g2.fillOval(cx - 6, (int) (h * 0.68), 12, 12);
            g2.dispose();
        }
    }

    private static JTextArea plainText(String text, Color color, int size) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("Segoe UI", Font.PLAIN, size));
        area.setForeground(color);
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        return area;
    }

    private static void showDialog(Window parent, String area, String species,
                                   Assessment a, AreaHistory h,
                                   Runnable issueAction, List<String> next) {
        Color accent = levelColor(a.level);

        String message;
        String advice;
        if ("HIGH".equals(a.level)) {
            message = "Danger! Stay away and stay indoors.";
            advice = "Do not go near this area. Keep children and livestock "
                    + "indoors and inform the forest department immediately.";
        } else if ("MEDIUM".equals(a.level)) {
            message = "Be careful in this area.";
            advice = "Avoid travelling alone, move in groups and stay away "
                    + "from forest edges until the risk goes down.";
        } else {
            message = "Stay aware of your surroundings.";
            advice = "Risk is low right now, but keep watching the area and "
                    + "report any new sightings.";
        }

        JDialog dialog = new JDialog(parent, "Risk Alert",
                Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);

        JPanel banner = new JPanel(new BorderLayout(20, 0));
        banner.setBackground(accent);
        banner.setBorder(new EmptyBorder(18, 25, 18, 25));

        JLabel small = new JLabel("RISK ALERT  -  " + area.toUpperCase());
        small.setFont(new Font("Segoe UI", Font.BOLD, 12));
        small.setForeground(new Color(255, 255, 255, 220));

        JLabel big = new JLabel(a.level + " RISK");
        big.setFont(new Font("Segoe UI", Font.BOLD, 32));
        big.setForeground(Color.WHITE);

        JLabel msg = new JLabel(message + "   (score " + a.score + ")");
        msg.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        msg.setForeground(Color.WHITE);

        JPanel bannerText = new JPanel();
        bannerText.setOpaque(false);
        bannerText.setLayout(new BoxLayout(bannerText, BoxLayout.Y_AXIS));
        bannerText.add(small);
        bannerText.add(Box.createVerticalStrut(2));
        bannerText.add(big);
        bannerText.add(Box.createVerticalStrut(4));
        bannerText.add(msg);

        banner.add(new WarningIcon(accent), BorderLayout.WEST);
        banner.add(bannerText, BorderLayout.CENTER);

        JPanel body = new JPanel();
        body.setBackground(Color.WHITE);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(16, 25, 8, 25));

        if (species != null) {
            JLabel reported = new JLabel("Reported animal:  " + species);
            reported.setFont(new Font("Segoe UI", Font.BOLD, 14));
            reported.setForeground(TEXT);
            reported.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(reported);
            body.add(Box.createVerticalStrut(10));
        }

        JLabel histTitle = new JLabel("Previously found in this area (last "
                + LOOKBACK_DAYS + " days)");
        histTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        histTitle.setForeground(DARK_GREEN);
        histTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(histTitle);
        body.add(Box.createVerticalStrut(6));

        DefaultTableModel model = new DefaultTableModel(
                new String[] { "Animal", "Times Seen", "Conflicts", "Last Seen" }, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy");
        for (SpeciesHistory s : h.items) {
            model.addRow(new Object[] {
                    h.nameOf(s), s.count, s.conflicts,
                    s.lastSeen == null ? "-" : fmt.format(s.lastSeen) });
        }
        if (h.items.isEmpty()) {
            model.addRow(new Object[] { "No earlier sightings", "-", "-", "-" });
        }
        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setRowHeight(24);
        table.setGridColor(BORDER);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setReorderingAllowed(false);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        tableScroll.setPreferredSize(new Dimension(440, 120));
        tableScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(tableScroll);
        body.add(Box.createVerticalStrut(12));

        JLabel whyTitle = new JLabel("Why this risk level?");
        whyTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        whyTitle.setForeground(DARK_GREEN);
        whyTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(whyTitle);
        body.add(Box.createVerticalStrut(4));

        StringBuilder why = new StringBuilder();
        for (String r : a.reasons) {
            why.append("- ").append(r).append("\n");
        }
        body.add(plainText(why.toString().trim(), MUTED, 12));

        if (next != null && !next.isEmpty()) {
            body.add(Box.createVerticalStrut(12));
            JLabel nextTitle = new JLabel("What happens next");
            nextTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
            nextTitle.setForeground(DARK_GREEN);
            nextTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(nextTitle);
            body.add(Box.createVerticalStrut(4));
            body.add(plainText(String.join("\n\n", next), TEXT, 12));
        }

        JTextArea adviceArea = new JTextArea(advice);
        adviceArea.setEditable(false);
        adviceArea.setLineWrap(true);
        adviceArea.setWrapStyleWord(true);
        adviceArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        adviceArea.setForeground(TEXT);
        adviceArea.setBackground(new Color(
                255 - (255 - accent.getRed()) * 12 / 100,
                255 - (255 - accent.getGreen()) * 12 / 100,
                255 - (255 - accent.getBlue()) * 12 / 100));
        adviceArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                new EmptyBorder(10, 12, 10, 12)));

        JButton ok = new JButton("I Understand");
        ok.setFont(new Font("Segoe UI", Font.BOLD, 14));
        ok.setForeground(Color.WHITE);
        ok.setBackground(accent);
        ok.setOpaque(true);
        ok.setFocusPainted(false);
        ok.setBorder(new EmptyBorder(10, 30, 10, 30));
        ok.setCursor(new Cursor(Cursor.HAND_CURSOR));
        ok.addActionListener(e -> dialog.dispose());

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonRow.setOpaque(false);

        if (issueAction != null) {
            JButton issue = new JButton("Issue Alert");
            issue.setFont(new Font("Segoe UI", Font.BOLD, 14));
            issue.setForeground(DARK_GREEN);
            issue.setBackground(Color.WHITE);
            issue.setOpaque(true);
            issue.setFocusPainted(false);
            issue.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(GREEN),
                    new EmptyBorder(9, 24, 9, 24)));
            issue.setCursor(new Cursor(Cursor.HAND_CURSOR));
            issue.addActionListener(e -> {
                dialog.dispose();
                issueAction.run();
            });
            buttonRow.add(issue);
        }
        buttonRow.add(ok);

        JPanel footer = new JPanel(new BorderLayout(0, 14));
        footer.setBackground(Color.WHITE);
        footer.setBorder(new EmptyBorder(6, 25, 20, 25));
        footer.add(adviceArea, BorderLayout.CENTER);
        footer.add(buttonRow, BorderLayout.SOUTH);

        root.add(banner, BorderLayout.NORTH);
        root.add(body, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.setResizable(false);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);

        if ("HIGH".equals(a.level)) {
            Toolkit.getDefaultToolkit().beep();
        }
        dialog.setVisible(true);
    }
}
