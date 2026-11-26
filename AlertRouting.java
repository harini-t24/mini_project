package oracletest;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides who sees which risk alert, and at what level.
 *
 * Rule: the animal's own risk sets the top level, and distance lowers it
 * one step at a time.
 *
 *   sighting's risk | same area | rest of city | rest of district | rest of state
 *   HIGH            | HIGH      | MEDIUM       | LOW              | none
 *   MEDIUM          | MEDIUM    | LOW          | none             | none
 *   LOW             | officers only (no public alert)
 *
 * Never public (officers and admins only): domestic / stray animals, rare
 * species, and LOW-risk animals.
 * Admins and officers see every alert at its original level.
 *
 * One alert is stored per sighting. Each person's level is worked out here,
 * when the dashboard loads, by comparing the person's AREA / CITY / DISTRICT
 * in USER_ACCOUNT with the sighting's.
 */
final class AlertRouting {

    private AlertRouting() { }

    // ANIMAL_CATEGORY values that count as "domestic" (never a public alert).
    // Matched as "contains", case-insensitive. Change to match your data.
    private static final String[] DOMESTIC_WORDS =
            { "DOMESTIC", "STRAY", "PET", "LIVESTOCK" };

    // Alert row layout (indexes 0-6 are what CommonDashboard already used)
    static final int KEY = 0, AREA = 1, SPECIES = 2, LEVEL = 3, DATE = 4,
            RAW_ID = 5, SCORE = 6, UNVERIFIED = 7, KIND = 8, SIGHTING = 9;

    static final String KIND_ALERT = "ALERT";
    static final String KIND_CLEAR = "CLEAR";   // "Area clear" notice

    /** The logged-in person's own location. */
    static final class Viewer {
        String area = "";
        String city = "";
        String district = "";
    }

    // ---------------------------------------------------------------
    // Column discovery (USER_ACCOUNT column names vary between projects)
    // ---------------------------------------------------------------

    private static String areaCol;
    private static String cityCol;
    private static String districtCol;
    private static String roleCol;
    private static boolean discovered = false;

    private static synchronized void discover(Connection con) throws SQLException {
        if (discovered) {
            return;
        }
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT * FROM USER_ACCOUNT WHERE 1 = 0")) {
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                String c = meta.getColumnLabel(i).toUpperCase();
                if (c.equals("AREA") || c.equals("AREA_NAME")) {
                    areaCol = c;
                } else if (c.equals("CITY")) {
                    cityCol = c;
                } else if (c.equals("DISTRICT")) {
                    districtCol = c;
                } else if (c.contains("ROLE") && roleCol == null) {
                    roleCol = c;
                }
            }
        }
        discovered = true;
    }

    // ---------------------------------------------------------------
    // Viewer
    // ---------------------------------------------------------------

    static Viewer loadViewer(Connection con, int userId) throws SQLException {
        discover(con);
        Viewer v = new Viewer();
        List<String> cols = new ArrayList<String>();
        if (areaCol != null) cols.add(areaCol);
        if (cityCol != null) cols.add(cityCol);
        if (districtCol != null) cols.add(districtCol);
        if (cols.isEmpty()) {
            return v;
        }
        String sql = "SELECT " + String.join(", ", cols)
                + " FROM USER_ACCOUNT WHERE USER_ID = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    if (areaCol != null) v.area = clean(rs.getString(areaCol));
                    if (cityCol != null) v.city = clean(rs.getString(cityCol));
                    if (districtCol != null) v.district = clean(rs.getString(districtCol));
                }
            }
        }
        return v;
    }

    // ---------------------------------------------------------------
    // Level maths
    // ---------------------------------------------------------------

    static int rank(String level) {
        String l = level == null ? "" : level.trim().toUpperCase();
        if (l.contains("HIGH")) return 3;
        if (l.contains("MEDIUM")) return 2;
        if (l.contains("LOW")) return 1;
        return 0;
    }

    static String name(int rank) {
        return rank >= 3 ? "HIGH" : rank == 2 ? "MEDIUM" : rank == 1 ? "LOW" : "";
    }

    /** 0 = same area, 1 = rest of city, 2 = rest of district, 3 = elsewhere. */
    static int distanceTier(Viewer v, String area, String city, String district) {
        boolean cityKnown = !v.city.isEmpty() && !clean(city).isEmpty();
        boolean cityMatch = cityKnown && same(v.city, city);
        boolean areaMatch = same(v.area, area) && (!cityKnown || cityMatch);
        if (areaMatch) {
            return 0;
        }
        if (cityMatch) {
            return 1;
        }
        boolean districtMatch = !v.district.isEmpty()
                && !clean(district).isEmpty() && same(v.district, district);
        return districtMatch ? 2 : 3;
    }

    /** Level this person should see, or 0 when they get no alert. */
    static int publicLevel(Viewer v, int baseRank, String area, String city,
                           String district) {
        if (baseRank < 2) {
            return 0;                       // LOW: officers only
        }
        int level = baseRank - distanceTier(v, area, city, district);
        return Math.max(level, 0);
    }

    private static boolean isDomestic(String category) {
        String c = category == null ? "" : category.toUpperCase();
        for (String w : DOMESTIC_WORDS) {
            if (c.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean same(String a, String b) {
        String x = clean(a);
        String y = clean(b);
        return !x.isEmpty() && x.equalsIgnoreCase(y);
    }

    // ---------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------

    private static String baseSql(String where, String order, int limit) {
        String roleSelect = roleCol == null ? "NULL" : "ru." + roleCol;
        return "SELECT a.ALERT_ID, a.AREA_NAME AS A_AREA, a.SPECIES_NAME AS A_SPECIES, "
                + "a.RISK_LEVEL, a.ALERT_DATE, a.RISK_SCORE, "
                + "s.SIGHTING_ID, s.AREA_NAME AS S_AREA, s.CITY AS S_CITY, "
                + "s.DISTRICT AS S_DISTRICT, s.IS_RARE, s.ANIMAL_CATEGORY, "
                + "s.VERIFICATION_STATUS, " + roleSelect + " AS REPORTER_ROLE "
                + "FROM RISK_ALERT a "
                + "LEFT JOIN RISK_RECORD r ON r.RISK_ID = a.RISK_ID "
                + "LEFT JOIN ANIMAL_SIGHTING s ON s.SIGHTING_ID = r.SIGHTING_ID "
                + "LEFT JOIN USER_ACCOUNT ru ON ru.USER_ID = s.REPORTED_BY "
                + "WHERE " + where + " ORDER BY " + order
                + " FETCH FIRST " + limit + " ROWS ONLY";
    }

    /**
     * Every alert this person may see, as rows
     * { key, area, species, level, date, rawId, score, unverified, kind, sightingId }.
     * Active alerts come first (newest first), then recent "Area clear" notices.
     * seeAll = true for admins and officers (original level, nothing hidden).
     */
    static List<String[]> load(Connection con, int userId, boolean seeAll)
            throws SQLException {
        discover(con);
        Viewer v = seeAll ? new Viewer() : loadViewer(con, userId);

        List<String[]> out = new ArrayList<String[]>();
        out.addAll(query(con, v, seeAll, KIND_ALERT, baseSql(
                "UPPER(NVL(a.STATUS, 'Active')) <> 'RESOLVED'",
                "a.ALERT_DATE DESC, a.ALERT_ID DESC", 300)));
        // alerts closed in the last 24 hours -> "Area clear"
        out.addAll(query(con, v, seeAll, KIND_CLEAR, baseSql(
                "UPPER(NVL(a.STATUS, 'Active')) = 'RESOLVED' "
                        + "AND UPPER(NVL(s.VERIFICATION_STATUS, '')) LIKE 'SOLV%' "
                        + "AND s.STATUS_UPDATED_DATE >= SYSDATE - 1",
                "s.STATUS_UPDATED_DATE DESC", 30)));
        return out;
    }

    private static List<String[]> query(Connection con, Viewer v, boolean seeAll,
            String kind, String sql) throws SQLException {
        List<String[]> list = new ArrayList<String[]>();
        SimpleDateFormat format = new SimpleDateFormat("dd MMM yyyy");

        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String rawId = rs.getString("ALERT_ID");
                String sightingId = rs.getString("SIGHTING_ID");
                String level = rs.getString("RISK_LEVEL");
                int base = rank(level);

                String sArea = rs.getString("S_AREA");
                String area = clean(sArea).isEmpty() ? rs.getString("A_AREA") : sArea;
                String species = rs.getString("A_SPECIES");

                boolean rare = "1".equals(rs.getString("IS_RARE"));
                boolean domestic = isDomestic(rs.getString("ANIMAL_CATEGORY"));

                String shown = level;
                if (!seeAll) {
                    if (rare || domestic) {
                        continue;                       // private to officers
                    }
                    int eff = publicLevel(v, base, area,
                            rs.getString("S_CITY"), rs.getString("S_DISTRICT"));
                    if (eff == 0) {
                        continue;
                    }
                    shown = name(eff);
                }

                String status = clean(rs.getString("VERIFICATION_STATUS")).toUpperCase();
                boolean pending = status.isEmpty() || status.startsWith("PEND");
                String role = clean(rs.getString("REPORTER_ROLE")).toUpperCase();
                boolean byPublic = !role.isEmpty() && !role.contains("ADMIN")
                        && !role.contains("OFFICER");
                boolean unverified = kind.equals(KIND_ALERT)
                        && base == 3 && pending && byPublic;

                Timestamp date = rs.getTimestamp("ALERT_DATE");
                String dateText = date == null ? "" : format.format(date);
                String key = rawId != null
                        ? rawId
                        : String.valueOf((area + species + dateText).hashCode());
                if (kind.equals(KIND_CLEAR)) {
                    key = "CLR" + key;
                }

                list.add(new String[] { key, area, species, shown, dateText,
                        rawId, rs.getString("RISK_SCORE"),
                        unverified ? "1" : "0", kind, sightingId });
            }
        }
        return list;
    }

    // ---------------------------------------------------------------
    // Helpers for the dashboard
    // ---------------------------------------------------------------

    /** HIGH / MEDIUM / LOW counts of the active alerts in this list. */
    static Map<String, Integer> countByLevel(List<String[]> rows) {
        Map<String, Integer> data = new LinkedHashMap<String, Integer>();
        data.put("HIGH", 0);
        data.put("MEDIUM", 0);
        data.put("LOW", 0);
        for (String[] r : rows) {
            if (!KIND_ALERT.equals(r[KIND])) {
                continue;
            }
            String n = name(rank(r[LEVEL]));
            if (!n.isEmpty()) {
                data.put(n, data.get(n) + 1);
            }
        }
        return data;
    }

    /** Alert ids this person may see (used to filter the Risk Alerts table). */
    static List<String> visibleAlertIds(List<String[]> rows) {
        List<String> ids = new ArrayList<String>();
        for (String[] r : rows) {
            if (KIND_ALERT.equals(r[KIND]) && r[RAW_ID] != null
                    && r[RAW_ID].matches("[A-Za-z0-9_-]+")) {
                ids.add(r[RAW_ID]);
            }
        }
        return ids;
    }
}