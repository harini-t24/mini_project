package oracletest;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * In-dashboard screens for the rescue workflow (same style as the other
 * table views). One class, two modes: CASES and TEAMS.
 */
class RescuePanel extends JPanel {

    enum Mode { CASES, TEAMS }

    private final Color background = new Color(246, 248, 245);
    private final Color darkGreen = new Color(24, 75, 53);
    private final Color green = new Color(53, 125, 85);
    private final Color lightGreen = new Color(226, 241, 230);
    private final Color textColor = new Color(45, 55, 48);
    private final Color mutedText = new Color(115, 125, 117);
    private final Color borderColor = new Color(225, 232, 225);
    private final Color red = new Color(204, 76, 76);
    private final Color amber = new Color(235, 174, 72);

    private final Mode mode;
    private final int userId;
    private final Window owner;

    private final JTable table = new JTable();
    private final JLabel countLabel = new JLabel("0 record(s)");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM, HH:mm");

    // model column positions (hidden helper columns come last)
    private static final int C_ID = 0, C_TYPE = 1, C_PRIORITY = 2, C_ANIMAL = 3,
            C_AREA = 4, C_STAGE = 5, C_TEAM = 6, C_REPORTED = 7, C_OUTCOME = 8,
            C_STAGE_CODE = 9, C_PRIORITY_NO = 10, C_SKILL = 11, C_TEAM_ID = 12,
            C_SIGHTING = 13, C_DESC = 14, C_PHONE = 15, C_CITY = 16;

    RescuePanel(Window owner, int userId, Mode mode, Runnable onBack) {
        this.owner = owner;
        this.userId = userId;
        this.mode = mode;

        setLayout(new BorderLayout());
        setBackground(background);
        setBorder(new EmptyBorder(25, 30, 25, 30));

        boolean cases = mode == Mode.CASES;

        JButton back = button("Back to Dashboard", false);
        back.addActionListener(e -> onBack.run());
        JButton refresh = button("Refresh", true);
        refresh.addActionListener(e -> load());

        List<JButton> buttons = new ArrayList<JButton>();
        buttons.add(back);
        if (cases) {
            JButton open = button("Open Case from Sighting", false);
            open.addActionListener(e -> openFromSighting());
            buttons.add(open);
        } else {
            JButton add = button("Add Team", true);
            add.addActionListener(e -> showTeamDialog(null));
            JButton delete = button("Delete Team", false);
            delete.addActionListener(e -> deleteSelectedTeam());
            buttons.add(delete);
            buttons.add(add);
        }
        buttons.add(refresh);

        add(header(cases ? "Rescue Cases" : "Rescue Teams",
                cases ? "Report to rescue and close: track every case and its team"
                      : "Teams, their skill and area, and whether they are free",
                buttons), BorderLayout.NORTH);

        table.setAutoCreateRowSorter(true);
        styleTable();
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    if (RescuePanel.this.mode == Mode.CASES) {
                        showCaseDialog();
                    } else {
                        showTeamDialog(selectedTeamRow());
                    }
                }
            }
        });

        countLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        countLabel.setForeground(textColor);
        JLabel hint = new JLabel(cases
                ? "Double-click a case to move it forward or close it"
                : "Double-click a team to edit it or change its availability");
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        hint.setForeground(mutedText);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(countLabel, BorderLayout.WEST);
        top.add(hint, BorderLayout.EAST);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(borderColor));
        scroll.getViewport().setBackground(Color.WHITE);

        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor),
                new EmptyBorder(15, 15, 15, 15)));
        card.add(top, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(background);
        wrapper.setBorder(new EmptyBorder(25, 0, 0, 0));
        wrapper.add(card, BorderLayout.CENTER);
        add(wrapper, BorderLayout.CENTER);
    }

    // ------------------------------------------------------------
    // small UI helpers
    // ------------------------------------------------------------

    private JButton button(String text, boolean primary) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        if (primary) {
            b.setForeground(Color.WHITE);
            b.setBackground(green);
            b.setBorder(new EmptyBorder(10, 20, 10, 20));
        } else {
            b.setForeground(darkGreen);
            b.setBackground(Color.WHITE);
            b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(green),
                    new EmptyBorder(9, 19, 9, 19)));
        }
        return b;
    }

    private JPanel header(String title, String sub, List<JButton> buttons) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(background);
        JPanel heading = new JPanel();
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.setBackground(background);
        JLabel t = new JLabel(title);
        t.setFont(new Font("Segoe UI", Font.BOLD, 28));
        t.setForeground(darkGreen);
        JLabel s = new JLabel(sub);
        s.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        s.setForeground(mutedText);
        heading.add(t);
        heading.add(Box.createVerticalStrut(5));
        heading.add(s);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setBackground(background);
        for (JButton b : buttons) {
            actions.add(b);
        }
        header.add(heading, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private void styleTable() {
        table.setRowHeight(32);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setForeground(textColor);
        table.setGridColor(borderColor);
        table.setSelectionBackground(lightGreen);
        table.setSelectionForeground(textColor);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setBackground(lightGreen);
        table.getTableHeader().setForeground(darkGreen);
        table.setDefaultEditor(Object.class, null);
    }

    private void error(String message, Exception ex) {
        JOptionPane.showMessageDialog(owner, message + "\n" + ex.getMessage(),
                "Database Error", JOptionPane.ERROR_MESSAGE);
    }

    private String nz(Object o, String fallback) {
        return o == null || String.valueOf(o).trim().isEmpty() ? fallback
                : String.valueOf(o);
    }

    private String when(Timestamp t) {
        return t == null ? "-" : dateFormat.format(t);
    }

    // ------------------------------------------------------------
    // loading
    // ------------------------------------------------------------

    void load() {
        SwingWorker<DefaultTableModel, Void> worker =
                new SwingWorker<DefaultTableModel, Void>() {
            @Override
            protected DefaultTableModel doInBackground() throws Exception {
                try (Connection con = DBConnection.getConnection()) {
                    return mode == Mode.CASES ? loadCases(con) : loadTeams(con);
                }
            }

            @Override
            protected void done() {
                try {
                    DefaultTableModel model = get();
                    table.setModel(model);
                    styleTable();
                    if (mode == Mode.CASES) {
                        // helper columns stay in the model but are not shown
                        for (int i = model.getColumnCount() - 1; i >= C_STAGE_CODE; i--) {
                            table.removeColumn(table.getColumnModel().getColumn(i));
                        }
                        table.getColumnModel().getColumn(C_PRIORITY)
                                .setCellRenderer(new PriorityRenderer());
                    } else {
                        table.removeColumn(table.getColumnModel().getColumn(0));
                    }
                    countLabel.setText(model.getRowCount() + " record(s)");
                } catch (Exception ex) {
                    error("Unable to open the rescue screen.\n"
                            + "(Have you run rescue_schema.sql?)", ex);
                }
            }
        };
        worker.execute();
    }

    private DefaultTableModel loadCases(Connection con) throws SQLException {
        RescueService.syncCases(con);

        DefaultTableModel model = new DefaultTableModel(new String[] {
                "ID", "Type", "Priority", "Animal", "Area", "Stage", "Team",
                "Reported", "Outcome", "STAGE_CODE", "PRIORITY_NO", "SKILL",
                "TEAM_ID", "SIGHTING_ID", "DESC", "PHONE", "CITY" }, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        String sql = "SELECT c.CASE_ID, c.CASE_TYPE, c.PRIORITY, c.SPECIES_NAME, "
                + "c.AREA_NAME, c.STAGE, t.TEAM_NAME, c.REPORTED_AT, c.OUTCOME, "
                + "c.NEEDED_SKILL, c.TEAM_ID, c.SIGHTING_ID, c.DESCRIPTION, "
                + "c.CONTACT_PHONE, c.CITY "
                + "FROM RESCUE_CASE c LEFT JOIN RESCUE_TEAM t ON t.TEAM_ID = c.TEAM_ID "
                + "ORDER BY CASE WHEN c.STAGE = 'CLOSED' THEN 1 ELSE 0 END, "
                + "c.PRIORITY, c.REPORTED_AT DESC";
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int p = rs.getInt("PRIORITY");
                String stage = rs.getString("STAGE");
                boolean human = RescueService.TYPE_HUMAN.equals(rs.getString("CASE_TYPE"));
                model.addRow(new Object[] {
                        rs.getInt("CASE_ID"),
                        human ? "SOS - human safety" : "Animal",
                        RescueService.priorityLabel(p),
                        nz(rs.getString("SPECIES_NAME"), "-"),
                        nz(rs.getString("AREA_NAME"), "-"),
                        RescueService.stageLabel(stage),
                        nz(rs.getString("TEAM_NAME"), "Not assigned"),
                        when(rs.getTimestamp("REPORTED_AT")),
                        RescueService.label(RescueService.OUTCOMES,
                                RescueService.OUTCOME_LABELS, rs.getString("OUTCOME")),
                        stage, p, rs.getString("NEEDED_SKILL"),
                        rs.getObject("TEAM_ID"), rs.getString("SIGHTING_ID"),
                        rs.getString("DESCRIPTION"), rs.getString("CONTACT_PHONE"),
                        rs.getString("CITY") });
            }
        }
        return model;
    }

    private DefaultTableModel loadTeams(Connection con) throws SQLException {
        DefaultTableModel model = new DefaultTableModel(new String[] {
                "ID", "Team", "Phone", "Area", "Skill", "Availability" }, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT TEAM_ID, TEAM_NAME, PHONE, AREA, "
                     + "SKILL, AVAILABLE FROM RESCUE_TEAM ORDER BY AREA, TEAM_NAME")) {
            while (rs.next()) {
                model.addRow(new Object[] { rs.getInt(1), rs.getString(2),
                        nz(rs.getString(3), "-"), rs.getString(4),
                        RescueService.label(RescueService.SKILLS,
                                RescueService.SKILL_LABELS, rs.getString(5)),
                        rs.getInt(6) == 1 ? "Free" : "Busy / off duty" });
            }
        }
        return model;
    }

    private class PriorityRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v,
                boolean sel, boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(t, v, sel, focus, row, col);
            String s = String.valueOf(v);
            c.setForeground(s.equals("URGENT") ? red : s.equals("HIGH") ? amber
                    : textColor);
            c.setFont(c.getFont().deriveFont(s.equals("NORMAL") ? Font.PLAIN : Font.BOLD));
            return c;
        }
    }

    // ------------------------------------------------------------
    // cases
    // ------------------------------------------------------------

    private void openFromSighting() {
        String id = JOptionPane.showInputDialog(owner,
                "Sighting ID to open a rescue case for:", "Open Rescue Case",
                JOptionPane.QUESTION_MESSAGE);
        if (id == null || id.trim().isEmpty()) {
            return;
        }
        try (Connection con = DBConnection.getConnection()) {
            boolean opened = RescueService.openFromSighting(con, id.trim());
            RescueService.autoAssignWaiting(con);
            JOptionPane.showMessageDialog(owner, opened
                    ? "Rescue case opened."
                    : "No new case was opened (the sighting was not found, "
                            + "or it already has a case).");
            load();
        } catch (SQLException ex) {
            error("Unable to open the case:", ex);
        }
    }

    private Object cell(int modelRow, int col) {
        return ((DefaultTableModel) table.getModel()).getValueAt(modelRow, col);
    }

    private void showCaseDialog() {
        int modelRow = table.convertRowIndexToModel(table.getSelectedRow());
        final int caseId = (Integer) cell(modelRow, C_ID);
        final String stage = String.valueOf(cell(modelRow, C_STAGE_CODE));
        final boolean human = String.valueOf(cell(modelRow, C_TYPE)).startsWith("SOS");
        final String skill = cell(modelRow, C_SKILL) == null ? "GENERAL"
                : String.valueOf(cell(modelRow, C_SKILL));
        final String area = String.valueOf(cell(modelRow, C_AREA));
        final boolean closed = stage.equals("CLOSED");
        final boolean hasTeam = cell(modelRow, C_TEAM_ID) != null;

        JDialog dialog = new JDialog(owner, "Rescue Case #" + caseId,
                Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);

        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.setBackground(human ? red : darkGreen);
        head.setBorder(new EmptyBorder(16, 22, 16, 22));
        JLabel title = new JLabel((human ? "SOS - " : "") + cell(modelRow, C_ANIMAL)
                + "  (" + cell(modelRow, C_PRIORITY) + " priority)");
        title.setFont(new Font("Segoe UI", Font.BOLD, 19));
        title.setForeground(Color.WHITE);
        JLabel sub = new JLabel("Stage: " + cell(modelRow, C_STAGE));
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(new Color(235, 240, 235));
        head.add(title);
        head.add(Box.createVerticalStrut(4));
        head.add(sub);

        JPanel info = new JPanel(new GridBagLayout());
        info.setBackground(Color.WHITE);
        info.setBorder(new EmptyBorder(12, 22, 12, 22));
        String[][] rows = {
                { "Area", area + (cell(modelRow, C_CITY) == null ? ""
                        : ", " + cell(modelRow, C_CITY)) },
                { "Team", String.valueOf(cell(modelRow, C_TEAM)) },
                { "Skill needed", RescueService.label(RescueService.SKILLS,
                        RescueService.SKILL_LABELS, skill) },
                { "Reported", String.valueOf(cell(modelRow, C_REPORTED)) },
                { "Contact", nz(cell(modelRow, C_PHONE), "-") },
                { "Details", nz(cell(modelRow, C_DESC), "-") },
                { "Sighting ID", nz(cell(modelRow, C_SIGHTING), "-") },
                { "Outcome", nz(cell(modelRow, C_OUTCOME), "-") } };
        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.NORTHWEST;
        g.insets = new Insets(5, 0, 5, 14);
        for (int i = 0; i < rows.length; i++) {
            g.gridy = i;
            g.gridx = 0;
            JLabel k = new JLabel(rows[i][0]);
            k.setFont(new Font("Segoe UI", Font.BOLD, 12));
            k.setForeground(mutedText);
            k.setPreferredSize(new Dimension(110, 20));
            info.add(k, g);
            g.gridx = 1;
            JLabel v = new JLabel("<html><body style='width:300px'>"
                    + rows[i][1].replace("<", "&lt;") + "</body></html>");
            v.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            v.setForeground(textColor);
            info.add(v, g);
        }

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        footer.setBackground(Color.WHITE);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor));

        JButton closeDialog = button("Close", false);
        closeDialog.addActionListener(e -> dialog.dispose());
        footer.add(closeDialog);

        if (!closed) {
            JButton assign = button(hasTeam ? "Change Team" : "Assign Team", false);
            assign.addActionListener(e -> {
                if (assignDialog(caseId, area, skill)) {
                    dialog.dispose();
                    load();
                }
            });
            footer.add(assign);

            if (stage.equals("ASSIGNED")) {
                JButton way = button("Team On the Way", true);
                way.addActionListener(e -> {
                    runStep(() -> RescueService.advance(con(), caseId, "ON_THE_WAY"));
                    dialog.dispose();
                    load();
                });
                footer.add(way);
            }
            if (stage.equals("ON_THE_WAY") && !human) {
                JButton secured = button("Trapped / Rescued", true);
                secured.addActionListener(e -> {
                    runStep(() -> RescueService.advance(con(), caseId, "SECURED"));
                    dialog.dispose();
                    load();
                });
                footer.add(secured);
            }
            {   // an open case can be closed at any stage (e.g. solved on the spot)
                JButton close = button(human ? "Close - Person Safe"
                        : "Mark Solved / Close", true);
                close.addActionListener(e -> {
                    if (closeCase(caseId, human)) {
                        dialog.dispose();
                        load();
                    }
                });
                footer.add(close);
            }
        }

        root.add(head, BorderLayout.NORTH);
        root.add(info, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    // helpers for one-off DB steps inside button handlers
    private Connection openCon;

    private Connection con() throws SQLException {
        if (openCon == null || openCon.isClosed()) {
            openCon = DBConnection.getConnection();
        }
        return openCon;
    }

    private interface Step {
        void run() throws SQLException;
    }

    private void runStep(Step step) {
        try {
            step.run();
        } catch (SQLException ex) {
            error("Unable to update the case:", ex);
        } finally {
            try {
                if (openCon != null) {
                    openCon.close();
                }
            } catch (SQLException ignored) {
                // nothing to do
            }
            openCon = null;
        }
    }

    private boolean assignDialog(int caseId, String area, String skill) {
        List<Integer> ids = new ArrayList<Integer>();
        List<String> labels = new ArrayList<String>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT TEAM_ID, TEAM_NAME, SKILL, AREA FROM RESCUE_TEAM "
                     + "WHERE AVAILABLE = 1 ORDER BY "
                     + "CASE WHEN UPPER(TRIM(AREA)) = UPPER(TRIM(?)) THEN 0 ELSE 1 END, "
                     + "CASE WHEN SKILL = ? THEN 0 ELSE 1 END, TEAM_NAME")) {
            ps.setString(1, area);
            ps.setString(2, skill);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                    labels.add(rs.getString(2) + "  -  " + RescueService.label(
                            RescueService.SKILLS, RescueService.SKILL_LABELS,
                            rs.getString(3)) + "  -  " + rs.getString(4));
                }
            }
        } catch (SQLException ex) {
            error("Unable to load the teams:", ex);
            return false;
        }
        if (ids.isEmpty()) {
            JOptionPane.showMessageDialog(owner, "No rescue team is free right now.\n"
                    + "The case stays in the queue and is given to the next free team.");
            return false;
        }
        JComboBox<String> box = new JComboBox<String>(labels.toArray(new String[0]));
        int ok = JOptionPane.showConfirmDialog(owner, box, "Choose a free team",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return false;
        }
        try (Connection con = DBConnection.getConnection()) {
            RescueService.assign(con, caseId, ids.get(box.getSelectedIndex()));
            return true;
        } catch (SQLException ex) {
            error("Unable to assign the team:", ex);
            return false;
        }
    }

    private boolean closeCase(int caseId, boolean human) {
        String outcome = null;
        if (!human) {
            JComboBox<String> box = new JComboBox<String>(RescueService.OUTCOME_LABELS);
            int ok = JOptionPane.showConfirmDialog(owner, box,
                    "How did the case end?", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) {
                return false;
            }
            outcome = RescueService.OUTCOMES[box.getSelectedIndex()];
        }
        try (Connection con = DBConnection.getConnection()) {
            RescueService.close(con, caseId, outcome, userId);
            JOptionPane.showMessageDialog(owner, human
                    ? "Case closed."
                    : "Case closed. The public alert is closed and an "
                            + "\"Area clear\" notice goes out.");
            return true;
        } catch (SQLException ex) {
            error("Unable to close the case:", ex);
            return false;
        }
    }

    // ------------------------------------------------------------
    // teams
    // ------------------------------------------------------------

    private Object[] selectedTeamRow() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        int r = table.convertRowIndexToModel(viewRow);
        DefaultTableModel m = (DefaultTableModel) table.getModel();
        Object[] row = new Object[m.getColumnCount()];
        for (int i = 0; i < row.length; i++) {
            row[i] = m.getValueAt(r, i);
        }
        return row;
    }

    private void deleteSelectedTeam() {
        Object[] row = selectedTeamRow();
        if (row == null) {
            JOptionPane.showMessageDialog(owner, "Select a team first.");
            return;
        }
        int ok = JOptionPane.showConfirmDialog(owner,
                "Delete team \"" + row[1] + "\"?", "Delete Team",
                JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }
        try (Connection con = DBConnection.getConnection()) {
            if (!RescueService.deleteTeam(con, (Integer) row[0])) {
                JOptionPane.showMessageDialog(owner,
                        "This team still has open cases, so it cannot be deleted.");
            }
            load();
        } catch (SQLException ex) {
            error("Unable to delete the team:", ex);
        }
    }

    // row = null for a new team, else the table row { id, name, phone, area, skill label, avail }
    private void showTeamDialog(Object[] row) {
        boolean isNew = row == null;

        JTextField name = new JTextField(isNew ? "" : String.valueOf(row[1]), 20);
        JTextField phone = new JTextField(isNew || "-".equals(row[2]) ? ""
                : String.valueOf(row[2]), 20);
        JTextField area = new JTextField(isNew ? "" : String.valueOf(row[3]), 20);
        JComboBox<String> skill = new JComboBox<String>(RescueService.SKILL_LABELS);
        if (!isNew) {
            skill.setSelectedItem(String.valueOf(row[4]));
        }
        JCheckBox free = new JCheckBox("Free for new cases",
                isNew || "Free".equals(String.valueOf(row[5])));

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Team name"));
        form.add(name);
        form.add(new JLabel("Phone"));
        form.add(phone);
        form.add(new JLabel("Area"));
        form.add(area);
        form.add(new JLabel("Skill"));
        form.add(skill);
        form.add(new JLabel("Availability"));
        form.add(free);

        int ok = JOptionPane.showConfirmDialog(owner, form,
                isNew ? "Add Rescue Team" : "Edit Rescue Team",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return;
        }
        String n = name.getText().trim();
        String a = area.getText().trim();
        String p = phone.getText().trim();
        if (n.isEmpty() || a.isEmpty()) {
            JOptionPane.showMessageDialog(owner, "Team name and area are required.");
            return;
        }
        if (!p.isEmpty() && !p.matches("[0-9+ \\-]{7,20}")) {
            JOptionPane.showMessageDialog(owner, "Please enter a valid phone number.");
            return;
        }
        String code = RescueService.SKILLS[skill.getSelectedIndex()];
        try (Connection con = DBConnection.getConnection()) {
            if (isNew) {
                RescueService.addTeam(con, n, p, a, code, free.isSelected());
            } else {
                RescueService.updateTeam(con, (Integer) row[0], n, p, a, code,
                        free.isSelected());
            }
            load();
        } catch (SQLException ex) {
            error("Unable to save the team:", ex);
        }
    }
}