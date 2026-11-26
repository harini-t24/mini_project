package oracletest;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
public class CommonDashboard extends JFrame {
    private static final String USER_ID_COLUMN = "USER_ID";
    private static final String[] ALLOWED_TABLES = {
            "USER_ACCOUNT", "ANIMAL_SIGHTING", "RISK_RECORD",
            "RISK_ALERT", "REPORT"
    };
    // Sighting status codes stored in ANIMAL_SIGHTING.VERIFICATION_STATUS
    private static final String[] STATUS_CODES =
            { "PENDING", "SEEN", "PROGRESS", "SOLVED" };

    private static final String[] STATUS_LABELS =
            { "Pending", "Seen", "Under Progress", "Solved" };
    // Reports that can be generated
    private static final String[] REPORT_TYPES = {
            "Sightings Summary", "Risk Summary", "Alert Summary",
            "Rescue Cases", "Rescue Report", "Area Report"
    };

    private int userId;
    private String username;
    private String role;

    private final Color background = new Color(246, 248, 245);
    private final Color white = Color.WHITE;

    private final Color darkGreen = new Color(24, 75, 53);

    private final Color green = new Color(53, 125, 85);

    private final Color lightGreen = new Color(226, 241, 230);

    private final Color textColor = new Color(45, 55, 48);

    private final Color mutedText = new Color(115, 125, 117);

    private final Color borderColor = new Color(225, 232, 225);

    private final Color riskHigh = new Color(204, 76, 76);

    private final Color riskMedium = new Color(235, 174, 72);

    private final Color riskLow = new Color(76, 155, 105);
    private final Preferences seenPrefs;
    private CardLayout cardLayout;
    private JPanel cards;
    private JLabel sidebarUserLabel;
    private JLabel statA;
    private JLabel statB;
    private JLabel statC;
    private JLabel statD;
    private JTable usersTable;
    private JLabel usersCountLabel;
    private JTable dataTable;
    private JLabel dataTitleLabel;
    private JLabel dataSubLabel;
    private JLabel dataCountLabel;
    private JLabel dataHintLabel;
    private JButton generateButton;
    private String currentTableName = "ANIMAL_SIGHTING";
    private String currentTableTitle = "Records";
    private JPanel profileAvatar;
    private JLabel profileNameLabel;
    private JLabel profileRoleLabel;
    private JPanel profileDetailsPanel;
    private SimpleChart riskDonut;
    private SimpleChart speciesChart;
    private SimpleChart riskBar;
    private SimpleChart trendChart;
    private SimpleChart areaChart;
    private SimpleChart statusChart;
    private JPanel sightingListPanel;
    private JPanel reviewListPanel;
    private JPanel alertListPanel;
    private JLabel alertCardTitle;
    private String alertBaseTitle = "Recent Risk Alerts";

    private List<String[]> currentSightings = new ArrayList<String[]>();

    private List<String[]> currentReviews = new ArrayList<String[]>();

    private List<String[]> currentAlerts = new ArrayList<String[]>();
    private boolean startupPopupDone = false;
    // rescue screens (Admin and Forest Officer) and the alerts this person may see
    private RescuePanel casesPanel;
    private RescuePanel teamsPanel;

    private volatile List<String[]> routedAlerts = new ArrayList<String[]>();
    // reports dashboard
    private JRadioButton dayWiseRadio;
    private JRadioButton weekWiseRadio;
    private JRadioButton monthWiseRadio;
    private JSpinner reportFromDate;
    private JSpinner reportToDate;
    private JTable reportsTable;
    private JLabel reportSummaryLabel;
    private JLabel reportSightingsValue;
    private JLabel reportConflictsValue;
    private JLabel reportHighAlertsValue;
    private JLabel reportRescueValue;
    private JLabel reportDetailsLabel;
    private JLabel reportAlertLabel;
    private JLabel reportSummaryBody;
    private String currentReportPeriod = "Day Wise Report";

    private enum ChartType { DONUT, BAR, HBAR, LINE }
    public CommonDashboard(int userId, String username, String role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.seenPrefs = Preferences.userRoot()
                .node("wildlife_conflict/seen_alerts/user_" + userId);
        setTitle("Human-Animal Safety Alert and Rescue System");
        setSize(1250, 780);
        setMinimumSize(new Dimension(1000, 650));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(background);
        add(createSidebar(), BorderLayout.WEST);
        add(createMainContent(), BorderLayout.CENTER);
        setVisible(true);
        refreshDashboard();
    }

    private boolean isAdmin() {
        return role.equalsIgnoreCase("Admin");
    }

    private boolean isOfficer() {
        return role.equalsIgnoreCase("Forest Officer");
    }
    // Admin and Forest Officer can update sighting status and generate reports
    private boolean canManage() {
        return isAdmin() || isOfficer();
    }

    // ---------------------------------------------------------
    // STATUS HELPERS
    // ---------------------------------------------------------
    private String normStatus(String s) {
        String v = s == null ? "" : s.trim().toUpperCase();
        if (v.contains("SOLV")) {
            return "SOLVED";
        } else if (v.contains("PROG")) {
            return "PROGRESS";
        } else if (v.contains("SEEN")) {
            return "SEEN";
        }
        return "PENDING";
    }

    private String statusLabel(String code) {
        for (int i = 0; i < STATUS_CODES.length; i++) {
            if (STATUS_CODES[i].equals(code)) {
                return STATUS_LABELS[i];
            }
        }
        return code;
    }

    private Color statusColor(String code) {
        if (code.equals("SOLVED")) {
            return riskLow;
        } else if (code.equals("PROGRESS")) {
            return new Color(150, 110, 170);
        } else if (code.equals("SEEN")) {
            return new Color(70, 130, 180);
        }
        return riskMedium;
    }

    // ---------------------------------------------------------
    // SIDEBAR
    // ---------------------------------------------------------
    private JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setPreferredSize(new Dimension(235, 0));
        sidebar.setBackground(darkGreen);
        sidebar.setLayout(new BorderLayout());
        sidebar.setBorder(new EmptyBorder(25, 15, 20, 15));
        JPanel topPanel = new JPanel();
        topPanel.setBackground(darkGreen);
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        JLabel logo = new JLabel("WILDLIFE");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 25));
        logo.setForeground(Color.WHITE);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("CONFLICT MANAGEMENT");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        subtitle.setForeground(new Color(190, 215, 195));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        topPanel.add(logo);
        topPanel.add(Box.createVerticalStrut(5));
        topPanel.add(subtitle);
        topPanel.add(Box.createVerticalStrut(35));
        JLabel menuLabel = new JLabel("MAIN MENU");
        menuLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        menuLabel.setForeground(new Color(180, 205, 185));
        menuLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        topPanel.add(menuLabel);
        topPanel.add(Box.createVerticalStrut(12));
        JButton dashboardButton = createMenuButton("Dashboard");
        dashboardButton.addActionListener(e -> {
            cardLayout.show(cards, "DASHBOARD");
            refreshDashboard();
        });
        topPanel.add(dashboardButton);
        addMenuItem(topPanel, "My Profile", null);
        addRoleButtons(topPanel);
        sidebar.add(topPanel, BorderLayout.NORTH);
        JPanel bottomPanel = new JPanel();
        bottomPanel.setBackground(darkGreen);
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        JSeparator separator = new JSeparator();
        separator.setForeground(new Color(90, 125, 100));
        sidebarUserLabel = new JLabel(username);
        sidebarUserLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sidebarUserLabel.setForeground(Color.WHITE);
        JLabel roleLabel = new JLabel(role);
        roleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        roleLabel.setForeground(new Color(190, 215, 195));
        JButton sosButton = createMenuButton("SOS - I need help");
        sosButton.setForeground(Color.WHITE);
        sosButton.setBackground(new Color(190, 60, 60));
        sosButton.setOpaque(true);
        sosButton.addActionListener(e -> showSosDialog());
        JButton logoutButton = createMenuButton("Logout");
        logoutButton.setForeground(new Color(255, 220, 215));
        logoutButton.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to logout?", "Logout",
                    JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                dispose();
                new LoginPage();
            }
        });
        bottomPanel.add(separator);
        bottomPanel.add(Box.createVerticalStrut(18));
        bottomPanel.add(sidebarUserLabel);
        bottomPanel.add(Box.createVerticalStrut(5));
        bottomPanel.add(roleLabel);
        bottomPanel.add(Box.createVerticalStrut(20));
        bottomPanel.add(sosButton);
        bottomPanel.add(Box.createVerticalStrut(8));
        bottomPanel.add(logoutButton);
        sidebar.add(bottomPanel, BorderLayout.SOUTH);
        return sidebar;
    }

    private void addRoleButtons(JPanel panel) {
        if (isAdmin()) {
            addMenuItem(panel, "Manage Users", "USER_ACCOUNT");
            addMenuItem(panel, "Report Animal Sighting", "ANIMAL_SIGHTING");
            addMenuItem(panel, "Animal Sightings", "ANIMAL_SIGHTING");
            addMenuItem(panel, "Area Risk Check", null);
            addMenuItem(panel, "Risk Records", "RISK_RECORD");
            addMenuItem(panel, "Risk Alerts", "RISK_ALERT");
            addMenuItem(panel, "Rescue Cases", null);
            addMenuItem(panel, "Rescue Teams", null);
            addMenuItem(panel, "Reports", "REPORT");
        } else if (isOfficer()) {
            addMenuItem(panel, "Report Animal Sighting", "ANIMAL_SIGHTING");
            addMenuItem(panel, "Animal Sightings", "ANIMAL_SIGHTING");
            addMenuItem(panel, "Area Risk Check", null);
            addMenuItem(panel, "Risk Records", "RISK_RECORD");
            addMenuItem(panel, "Risk Alerts", "RISK_ALERT");
            addMenuItem(panel, "Rescue Cases", null);
            addMenuItem(panel, "Rescue Teams", null);
            addMenuItem(panel, "Reports", "REPORT");
        } else if (role.equalsIgnoreCase("General User")) {
            addMenuItem(panel, "Report Animal Sighting", "ANIMAL_SIGHTING");
            addMenuItem(panel, "Area Risk Check", null);
            addMenuItem(panel, "Risk Alerts", "RISK_ALERT");
            addMenuItem(panel, "Reports", "REPORT");
        }
    }

    private void addMenuItem(JPanel panel, String buttonText, String tableName) {
        JButton button = createMenuButton(buttonText);
        button.addActionListener(e -> {
            if (buttonText.equals("Report Animal Sighting")) {
                openReportSighting();
            } else if (buttonText.equals("My Profile")) {
                cardLayout.show(cards, "PROFILE");
                loadProfile();
            } else if (buttonText.equals("Manage Users")) {
                cardLayout.show(cards, "USERS");
                loadUsers();
            } else if (buttonText.equals("Area Risk Check")) {
                showAreaRiskCheck();
            } else if (buttonText.equals("Reports")) {
                cardLayout.show(cards, "REPORTS");
                generateDashboardReport(false);
            } else if (buttonText.equals("Rescue Cases")) {
                cardLayout.show(cards, "CASES");
                casesPanel.load();
            } else if (buttonText.equals("Rescue Teams")) {
                cardLayout.show(cards, "TEAMS");
                teamsPanel.load();
            } else {
                showTableView(tableName, buttonText);
            }
        });
        panel.add(button);
    }

    private void openReportSighting() {
        AddAnimalSighting page = new AddAnimalSighting(userId, username);
        page.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                refreshDashboard();
            }
        });
    }

    // ---------------------------------------------------------
    // SOS (human safety)
    // ---------------------------------------------------------
    private void showSosDialog() {
        AlertRouting.Viewer me = new AlertRouting.Viewer();
        try (Connection con = DBConnection.getConnection()) {
            me = AlertRouting.loadViewer(con, userId);
        } catch (SQLException ex) {
            // the person can still type the location below
        }
        JTextField areaField = new JTextField(me.area, 18);
        JTextField cityField = new JTextField(me.city, 18);
        JTextField districtField = new JTextField(me.district, 18);
        JTextField phoneField = new JTextField(18);
        JTextArea whatArea = new JTextArea(3, 18);
        whatArea.setLineWrap(true);
        whatArea.setWrapStyleWord(true);
        JLabel warn = new JLabel("<html><b>In immediate danger? Call 112 first.</b><br>"
                + "This sends an urgent request to forest officers and rescue teams.</html>");
        warn.setForeground(new Color(170, 40, 40));
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0;
        g.gridy = 0;
        g.gridwidth = 2;
        form.add(warn, g);
        g.gridwidth = 1;
        String[] labels = { "What is happening?", "Your phone", "Area", "City", "District" };
        JComponent[] fields = { new JScrollPane(whatArea), phoneField, areaField,
                cityField, districtField };
        for (int i = 0; i < labels.length; i++) {
            g.gridy = i + 1;
            g.gridx = 0;
            form.add(new JLabel(labels[i]), g);
            g.gridx = 1;
            form.add(fields[i], g);
        }
        int ok = JOptionPane.showConfirmDialog(this, form, "SOS - Human Safety",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return;
        }
        String what = whatArea.getText().trim();
        String phone = phoneField.getText().trim();
        String area = areaField.getText().trim();
        if (what.isEmpty() || area.isEmpty() || !phone.matches("[0-9+ \\-]{7,20}")) {
            JOptionPane.showMessageDialog(this,
                    "Please describe what is happening, give your area and a valid phone number.");
            return;
        }
        if (what.length() > 200) {
            what = what.substring(0, 200);
        }
        try (Connection con = DBConnection.getConnection()) {
            boolean assigned = RescueService.createSos(con, userId, what, phone,
                    area, cityField.getText().trim(), districtField.getText().trim());
            JOptionPane.showMessageDialog(this, assigned
                    ? "Your SOS has been sent and a rescue team has been assigned.\n"
                            + "Stay in a safe place. They may call you."
                    : "Your SOS has been sent to the forest officers.\n"
                            + "No team is free yet, so an officer will arrange help.\n"
                            + "Stay in a safe place and keep your phone on.",
                    "SOS Sent", JOptionPane.INFORMATION_MESSAGE);
            refreshDashboard();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not send the SOS. Please call 112 now.\n" + ex.getMessage(),
                    "SOS Failed", JOptionPane.ERROR_MESSAGE);
        }
    }
    // Officers and admins get a popup for each SOS they have not seen yet
    private void showNewSosPopups(List<String[]> sosCases) {
        if (!canManage()) {
            return;
        }
        for (String[] c : sosCases) {
            String key = "SOS" + c[0];
            if (isSeen(key)) {
                continue;
            }
            markSeen(key);
            JOptionPane.showMessageDialog(this,
                    "A person needs help.\n\nArea: " + nz(c[1], "-")
                            + "\nDetails: " + nz(c[2], "-")
                            + "\nPhone: " + nz(c[3], "-")
                            + "\n\nOpen Rescue Cases to follow it up.",
                    "SOS - Human Safety", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void showAreaRiskCheck() {
        String area = JOptionPane.showInputDialog(this,
                "Enter the area name:", "Area Risk Check",
                JOptionPane.QUESTION_MESSAGE);
        if (area == null || area.trim().isEmpty()) {
            return;
        }
        try {
            AreaRiskService.AreaHistory history =
                    AreaRiskService.loadHistory(area.trim());
            AreaRiskService.showAreaCheck(this, history, canManage(),
                    () -> refreshDashboard());
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Unable to load area history:\n" + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JButton createMenuButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(darkGreen);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(12, 12, 12, 5));
        button.setMaximumSize(new Dimension(210, 45));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(45, 100, 68));
            }
            public void mouseExited(MouseEvent e) {
                button.setBackground(darkGreen);
            }
        });
        return button;
    }

    // ---------------------------------------------------------
    // MAIN CONTENT
    // ---------------------------------------------------------
    private JPanel createMainContent() {
        cardLayout = new CardLayout();
        cards = new JPanel(cardLayout);
        cards.setBackground(background);
        cards.add(createDashboardView(), "DASHBOARD");
        cards.add(createProfileView(), "PROFILE");
        cards.add(createTableView(), "TABLE");
        cards.add(createReportsView(), "REPORTS");
        if (isAdmin()) {
            cards.add(createUsersView(), "USERS");
        }
        if (canManage()) {
            Runnable backToDashboard = () -> {
                cardLayout.show(cards, "DASHBOARD");
                refreshDashboard();
            };
            casesPanel = new RescuePanel(this, userId,
                    RescuePanel.Mode.CASES, backToDashboard);
            teamsPanel = new RescuePanel(this, userId,
                    RescuePanel.Mode.TEAMS, backToDashboard);
            cards.add(casesPanel, "CASES");
            cards.add(teamsPanel, "TEAMS");
        }
        cardLayout.show(cards, "DASHBOARD");
        return cards;
    }

    private JPanel createHeader(String titleText, String subText,
                                JButton... buttons) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(background);
        JPanel headingPanel = new JPanel();
        headingPanel.setLayout(new BoxLayout(headingPanel, BoxLayout.Y_AXIS));
        headingPanel.setBackground(background);
        JLabel title = new JLabel(titleText);
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        title.setForeground(darkGreen);
        JLabel sub = new JLabel(subText);
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        sub.setForeground(mutedText);
        headingPanel.add(title);
        headingPanel.add(Box.createVerticalStrut(5));
        headingPanel.add(sub);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setBackground(background);
        for (JButton b : buttons) {
            actions.add(b);
        }
        header.add(headingPanel, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);
        header.putClientProperty("titleLabel", title);
        header.putClientProperty("subLabel", sub);
        return header;
    }

    private JButton createActionButton(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        if (primary) {
            button.setForeground(Color.WHITE);
            button.setBackground(green);
            button.setBorder(new EmptyBorder(10, 20, 10, 20));
        } else {
            button.setForeground(darkGreen);
            button.setBackground(Color.WHITE);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(green),
                    new EmptyBorder(9, 19, 9, 19)));
        }
        return button;
    }

    // ---------------------------------------------------------
    // DASHBOARD VIEW
    // ---------------------------------------------------------
    private JPanel createDashboardView() {
        JPanel view = new JPanel(new BorderLayout());
        view.setBackground(background);
        view.setBorder(new EmptyBorder(25, 30, 25, 30));
        JButton refreshButton = createActionButton("Refresh", true);
        refreshButton.addActionListener(e -> refreshDashboard());
        String heading;
        JPanel content;
        if (isAdmin()) {
            heading = "Admin Dashboard";
            content = createAdminBody();
        } else if (isOfficer()) {
            heading = "Forest Officer Dashboard";
            content = createOfficerBody();
        } else {
            heading = "Community Dashboard";
            content = createGeneralBody();
        }
        view.add(createHeader(heading, "Welcome back, " + username,
                refreshButton), BorderLayout.NORTH);
        JScrollPane mainScroll = new JScrollPane(content);
        mainScroll.setBorder(null);
        mainScroll.getViewport().setBackground(background);
        mainScroll.getVerticalScrollBar().setUnitIncrement(16);
        view.add(mainScroll, BorderLayout.CENTER);
        return view;
    }

    private JPanel newBody() {
        JPanel content = new JPanel();
        content.setBackground(background);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(25, 0, 0, 0));
        return content;
    }

    private JPanel newRow(int columns, int height) {
        JPanel row = new JPanel(new GridLayout(1, columns, 15, 0));
        row.setBackground(background);
        row.setPreferredSize(new Dimension(0, height));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        return row;
    }

    private JPanel newListPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(white);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private JPanel createAdminBody() {
        JPanel content = newBody();
        statA = new JLabel("0");
        statB = new JLabel("0");
        statC = new JLabel("0");
        statD = new JLabel("0");
        JPanel stats = newRow(4, 115);
        stats.add(createStatCard("Total Users", statA, "Registered accounts"));
        stats.add(createStatCard("Animal Sightings", statB, "Recorded sightings"));
        stats.add(createStatCard("Risk Records", statC, "Risk assessments"));
        stats.add(createStatCard("Risk Alerts", statD, "Generated alerts"));
        content.add(stats);
        content.add(Box.createVerticalStrut(22));
        riskDonut = new SimpleChart(ChartType.DONUT);
        speciesChart = new SimpleChart(ChartType.HBAR);
        JPanel charts = newRow(2, 280);
        charts.add(createChartCard("Risk Level Distribution", riskDonut));
        charts.add(createChartCard("Top Species Sighted", speciesChart));
        content.add(charts);
        content.add(Box.createVerticalStrut(22));
        sightingListPanel = newListPanel();
        JPanel recent = newRow(2, 290);
        recent.add(createListCard(
                new JLabel("Recent Animal Sightings"), sightingListPanel));
        recent.add(createAlertCard("Recent Risk Alerts"));
        content.add(recent);
        return content;
    }

    private JPanel createOfficerBody() {
        JPanel content = newBody();
        statA = new JLabel("0");
        statB = new JLabel("0");
        statC = new JLabel("0");
        statD = new JLabel("0");
        JPanel stats = newRow(4, 115);
        stats.add(createStatCard("Animal Sightings", statA, "Recorded sightings"));
        stats.add(createStatCard("Pending Review", statB, "Awaiting officer review"));
        stats.add(createStatCard("High Risk", statC, "High risk records"));
        stats.add(createStatCard("Risk Alerts", statD, "Generated alerts"));
        statB.setForeground(riskMedium);
        statC.setForeground(riskHigh);
        content.add(stats);
        content.add(Box.createVerticalStrut(22));
        riskBar = new SimpleChart(ChartType.BAR);
        trendChart = new SimpleChart(ChartType.LINE);
        JPanel charts1 = newRow(2, 290);
        charts1.add(createChartCard("Risk Levels (Bar Graph)", riskBar));
        charts1.add(createChartCard("Sightings per Day (Latest 7 Days)", trendChart));
        content.add(charts1);
        content.add(Box.createVerticalStrut(22));
        areaChart = new SimpleChart(ChartType.HBAR);
        JPanel charts2 = newRow(2, 290);
        charts2.add(createChartCard("Alerts by Area", areaChart));
        charts2.add(createAlertCard("Recent Risk Alerts"));
        content.add(charts2);
        content.add(Box.createVerticalStrut(22));
        statusChart = new SimpleChart(ChartType.BAR);
        statusChart.statusColors = true;
        reviewListPanel = newListPanel();
        JPanel charts3 = newRow(2, 290);
        charts3.add(createChartCard("Sighting Status", statusChart));
        charts3.add(createListCard(
                new JLabel("Sightings To Review"), reviewListPanel));
        content.add(charts3);
        return content;
    }

    private JPanel createGeneralBody() {
        JPanel content = newBody();
        JPanel banner = new JPanel(new BorderLayout());
        banner.setBackground(darkGreen);
        banner.setBorder(new EmptyBorder(18, 25, 18, 25));
        banner.setPreferredSize(new Dimension(0, 95));
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 95));
        JPanel bannerText = new JPanel();
        bannerText.setOpaque(false);
        bannerText.setLayout(new BoxLayout(bannerText, BoxLayout.Y_AXIS));
        JLabel bannerTitle = new JLabel("Spotted an animal nearby?");
        bannerTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        bannerTitle.setForeground(Color.WHITE);
        JLabel bannerSub = new JLabel(
                "Report it quickly so officers can assess the risk and alert your area.");
        bannerSub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        bannerSub.setForeground(new Color(200, 225, 205));
        bannerText.add(bannerTitle);
        bannerText.add(Box.createVerticalStrut(6));
        bannerText.add(bannerSub);
        JButton reportButton = createActionButton("Report Animal Sighting", false);
        reportButton.addActionListener(e -> openReportSighting());
        JPanel buttonWrap = new JPanel(new GridBagLayout());
        buttonWrap.setOpaque(false);
        buttonWrap.add(reportButton);
        banner.add(bannerText, BorderLayout.WEST);
        banner.add(buttonWrap, BorderLayout.EAST);
        content.add(banner);
        content.add(Box.createVerticalStrut(22));
        statA = new JLabel("0");
        statB = new JLabel("0");
        statC = new JLabel("0");
        JPanel tiles = newRow(3, 105);
        tiles.add(createTile("High Alerts", statA, "Stay away, stay indoors", riskHigh));
        tiles.add(createTile("Medium Alerts", statB, "Be careful in these areas", riskMedium));
        tiles.add(createTile("Low Alerts", statC, "Stay aware of surroundings", riskLow));
        content.add(tiles);
        content.add(Box.createVerticalStrut(22));
        JPanel lower = newRow(2, 320);
        JPanel tipsCard = createCard();
        tipsCard.setLayout(new BorderLayout(0, 10));
        tipsCard.setBorder(new EmptyBorder(15, 15, 15, 15));
        JLabel tipsTitle = new JLabel("Safety Tips");
        tipsTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        tipsTitle.setForeground(textColor);
        JTextArea tips = new JTextArea(
                "- Never approach or feed wild animals.\n\n"
                + "- Avoid forest edges and farmland at dawn and dusk.\n\n"
                + "- Keep children and livestock indoors when a HIGH alert is active.\n\n"
                + "- Report every sighting quickly with the area and species name.\n\n"
                + "- Move in groups and carry a light when travelling near forests.");
        tips.setEditable(false);
        tips.setLineWrap(true);
        tips.setWrapStyleWord(true);
        tips.setOpaque(false);
        tips.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tips.setForeground(textColor);
        tipsCard.add(tipsTitle, BorderLayout.NORTH);
        tipsCard.add(tips, BorderLayout.CENTER);
        lower.add(createAlertCard("Latest Risk Alerts"));
        lower.add(tipsCard);
        content.add(lower);
        return content;
    }

    // ---------------------------------------------------------
    // CARDS
    // ---------------------------------------------------------
    private JPanel createStatCard(String heading, JLabel valueLabel,
                                  String description) {
        JPanel card = createCard();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(15, 17, 12, 12));
        JLabel headingLabel = new JLabel(heading);
        headingLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        headingLabel.setForeground(mutedText);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 27));
        valueLabel.setForeground(darkGreen);
        JLabel descriptionLabel = new JLabel(description);
        descriptionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        descriptionLabel.setForeground(mutedText);
        card.add(headingLabel);
        card.add(Box.createVerticalStrut(8));
        card.add(valueLabel);
        card.add(Box.createVerticalStrut(5));
        card.add(descriptionLabel);
        return card;
    }

    private JPanel createTile(String heading, JLabel valueLabel,
                              String description, Color accent) {
        JPanel card = new JPanel();
        card.setBackground(white);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 6, 0, 0, accent),
                        new EmptyBorder(14, 16, 12, 12))));
        JLabel headingLabel = new JLabel(heading);
        headingLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        headingLabel.setForeground(mutedText);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        valueLabel.setForeground(accent);
        JLabel descriptionLabel = new JLabel(description);
        descriptionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        descriptionLabel.setForeground(mutedText);
        card.add(headingLabel);
        card.add(Box.createVerticalStrut(6));
        card.add(valueLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(descriptionLabel);
        return card;
    }

    private JPanel createChartCard(String heading, JComponent chart) {
        JPanel card = createCard();
        card.setLayout(new BorderLayout());
        JLabel title = new JLabel(heading);
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setForeground(textColor);
        title.setBorder(new EmptyBorder(12, 15, 5, 10));
        card.add(title, BorderLayout.NORTH);
        card.add(chart, BorderLayout.CENTER);
        return card;
    }

    private JPanel createListCard(JLabel title, JPanel listPanel) {
        JPanel card = createCard();
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(15, 15, 15, 15));
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setForeground(textColor);
        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(BorderFactory.createLineBorder(borderColor));
        scroll.getViewport().setBackground(white);
        scroll.getVerticalScrollBar().setUnitIncrement(12);
        card.add(title, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private JPanel createAlertCard(String heading) {
        alertBaseTitle = heading;
        alertCardTitle = new JLabel(heading);
        alertListPanel = newListPanel();
        return createListCard(alertCardTitle, alertListPanel);
    }

    private JPanel createCard() {
        JPanel card = new JPanel();
        card.setBackground(white);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor),
                new EmptyBorder(5, 5, 5, 5)));
        return card;
    }

    private Color levelColor(String level) {
        String l = level == null ? "" : level.toUpperCase();
        if (l.contains("HIGH")) {
            return riskHigh;
        } else if (l.contains("MEDIUM")) {
            return riskMedium;
        } else if (l.contains("LOW")) {
            return riskLow;
        }
        return mutedText;
    }

    private String levelName(String level) {
        String l = level == null ? "" : level.trim().toUpperCase();
        if (l.contains("HIGH")) {
            return "HIGH";
        } else if (l.contains("MEDIUM")) {
            return "MEDIUM";
        } else if (l.contains("LOW")) {
            return "LOW";
        }
        return l.isEmpty() ? "UNKNOWN" : l;
    }

    private Color mix(Color c, double amount) {
        int r = (int) (255 - (255 - c.getRed()) * amount);
        int g = (int) (255 - (255 - c.getGreen()) * amount);
        int b = (int) (255 - (255 - c.getBlue()) * amount);
        return new Color(r, g, b);
    }

    private String nz(String value, String fallback) {
        return (value == null || value.trim().isEmpty()) ? fallback : value.trim();
    }

    private String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ---------------------------------------------------------
    // ALERT LIST (seen / unseen) + STARTUP POPUP
    // ---------------------------------------------------------
    private boolean isSeen(String key) {
        return key != null && seenPrefs.getBoolean(key, false);
    }

    private void markSeen(String key) {
        if (key != null) {
            seenPrefs.putBoolean(key, true);
        }
    }
    // alert = { key, area, species, level, date, rawId, score }
    private void showAlertFromRow(String[] alert) {
        markSeen(alert[0]);
        if (AlertRouting.KIND_CLEAR.equals(alert[AlertRouting.KIND])) {
            JOptionPane.showMessageDialog(this,
                    "The " + nz(alert[2], "animal") + " reported in "
                            + nz(alert[1], "your area") + " has been dealt with.\n"
                            + "You can go about your day as usual.",
                    "Area Clear", JOptionPane.INFORMATION_MESSAGE);
        } else {
            boolean unverified = "1".equals(alert[AlertRouting.UNVERIFIED]);
            showAlertDialog(alert[1],
                    alert[2] + (unverified ? " (Unverified report)" : ""),
                    alert[3], alert[4], alert[6]);
        }
        renderAlertList();
    }
    // Shows the most serious unseen alert as a warning popup after login
    private void showStartupAlert() {
        String[] pick = null;
        int bestRank = -1;
        for (String[] alert : currentAlerts) {
            if (isSeen(alert[0])
                    || AlertRouting.KIND_CLEAR.equals(alert[AlertRouting.KIND])) {
                continue;
            }
            String l = levelName(alert[3]);
            int rank = l.equals("HIGH") ? 3 : l.equals("MEDIUM") ? 2 : 1;
            if (rank > bestRank) {
                bestRank = rank;
                pick = alert;
            }
        }
        if (pick != null) {
            showAlertFromRow(pick);
        }
    }

    private void renderAlertList() {
        if (alertListPanel == null) {
            return;
        }
        alertListPanel.removeAll();
        int unseen = 0;
        if (currentAlerts.isEmpty()) {
            JLabel none = new JLabel("  No alerts at the moment. Stay safe!");
            none.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            none.setForeground(mutedText);
            none.setBorder(new EmptyBorder(14, 8, 14, 8));
            alertListPanel.add(none);
        } else {
            for (String[] alert : currentAlerts) {
                boolean seen = isSeen(alert[0]);
                if (!seen) {
                    unseen++;
                }
                alertListPanel.add(createAlertRow(alert, seen));
            }
        }
        alertListPanel.add(Box.createVerticalGlue());
        alertCardTitle.setText(unseen > 0
                ? alertBaseTitle + "   (" + unseen + " unseen)"
                : alertBaseTitle);
        alertListPanel.revalidate();
        alertListPanel.repaint();
    }

    private JPanel createAlertRow(String[] alert, boolean seen) {
        // seen alerts are faded, unseen alerts stay highlighted
        Color rowColor = seen ? new Color(244, 246, 244) : new Color(255, 240, 220);
        boolean clear = AlertRouting.KIND_CLEAR.equals(alert[AlertRouting.KIND]);
        boolean unverified = "1".equals(alert[AlertRouting.UNVERIFIED]);
        Color accent = clear ? riskLow : levelColor(alert[3]);
        if (seen) {
            accent = mix(accent, 0.35);
        }
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setBackground(rowColor);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 5, 0, 0, accent),
                        new EmptyBorder(8, 10, 8, 8))));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        row.setPreferredSize(new Dimension(0, 58));
        row.setCursor(new Cursor(Cursor.HAND_CURSOR));
        String titleText = clear
                ? "Area clear  -  " + nz(alert[1], "your area")
                : nz(alert[1], "Risk Alert") + "  -  " + levelName(alert[3]);
        JLabel main = new JLabel(titleText);
        main.setFont(new Font("Segoe UI", seen ? Font.PLAIN : Font.BOLD, 13));
        main.setForeground(seen ? mutedText : textColor);
        JLabel sub = new JLabel((unverified ? "UNVERIFIED   |   " : "")
                + nz(alert[2], "Unknown animal")
                + (clear ? "   |   no longer a danger" : "")
                + (alert[4].isEmpty() ? "" : "   |   " + alert[4]));
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(seen ? new Color(160, 168, 161) : mutedText);
        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        text.add(main);
        text.add(sub);
        row.add(text, BorderLayout.CENTER);
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showAlertFromRow(alert);
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                row.setBackground(lightGreen);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                row.setBackground(rowColor);
            }
        });
        return row;
    }

    // ---------------------------------------------------------
    // SIGHTING LISTS
    // ---------------------------------------------------------
    private void renderSightingList() {
        if (sightingListPanel == null) {
            return;
        }
        sightingListPanel.removeAll();
        if (currentSightings.isEmpty()) {
            JLabel none = new JLabel("  No animal sightings recorded yet.");
            none.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            none.setForeground(mutedText);
            none.setBorder(new EmptyBorder(14, 8, 14, 8));
            sightingListPanel.add(none);
        } else {
            for (String[] sighting : currentSightings) {
                sightingListPanel.add(createSightingRow(sighting));
            }
        }
        sightingListPanel.add(Box.createVerticalGlue());
        sightingListPanel.revalidate();
        sightingListPanel.repaint();
    }

    private JPanel createSightingRow(String[] sighting) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(white);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor),
                new EmptyBorder(8, 12, 8, 12)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        row.setPreferredSize(new Dimension(0, 44));
        row.setCursor(new Cursor(Cursor.HAND_CURSOR));
        JLabel name = new JLabel(nz(sighting[1], "Unknown animal"));
        name.setFont(new Font("Segoe UI", Font.BOLD, 13));
        name.setForeground(textColor);
        JLabel arrow = new JLabel(">");
        arrow.setFont(new Font("Segoe UI", Font.BOLD, 14));
        arrow.setForeground(green);
        row.add(name, BorderLayout.CENTER);
        row.add(arrow, BorderLayout.EAST);
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showSightingDialog(sighting[0]);
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                row.setBackground(lightGreen);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                row.setBackground(white);
            }
        });
        return row;
    }

    private void renderReviewList() {
        if (reviewListPanel == null) {
            return;
        }
        reviewListPanel.removeAll();
        if (currentReviews.isEmpty()) {
            JLabel none = new JLabel("  Nothing to review. All sightings are solved.");
            none.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            none.setForeground(mutedText);
            none.setBorder(new EmptyBorder(14, 8, 14, 8));
            reviewListPanel.add(none);
        } else {
            for (String[] s : currentReviews) {
                reviewListPanel.add(createReviewRow(s));
            }
        }
        reviewListPanel.add(Box.createVerticalGlue());
        reviewListPanel.revalidate();
        reviewListPanel.repaint();
    }
    // s = { id, species, area, statusCode, rare, rescue }
    private JPanel createReviewRow(String[] s) {
        String code = normStatus(s[3]);
        Color accent = statusColor(code);
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setBackground(white);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 5, 0, 0, accent),
                        new EmptyBorder(8, 10, 8, 8))));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        row.setPreferredSize(new Dimension(0, 54));
        row.setCursor(new Cursor(Cursor.HAND_CURSOR));
        JLabel main = new JLabel(nz(s[1], "Unknown animal") + "  -  "
                + nz(s[2], "Unknown area"));
        main.setFont(new Font("Segoe UI", Font.BOLD, 13));
        main.setForeground(textColor);
        String extra = "Sighting #" + s[0]
                + ("1".equals(s[4]) ? "   |   RARE" : "")
                + ("1".equals(s[5]) ? "   |   NEEDS RESCUE" : "");
        JLabel sub = new JLabel(extra);
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(mutedText);
        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        text.add(main);
        text.add(sub);
        JLabel badge = new JLabel(statusLabel(code).toUpperCase(),
                SwingConstants.CENTER);
        badge.setOpaque(true);
        badge.setFont(new Font("Segoe UI", Font.BOLD, 10));
        badge.setBackground(accent);
        badge.setForeground(Color.WHITE);
        badge.setPreferredSize(new Dimension(110, 24));
        JPanel badgeWrap = new JPanel(new GridBagLayout());
        badgeWrap.setOpaque(false);
        badgeWrap.add(badge);
        row.add(text, BorderLayout.CENTER);
        row.add(badgeWrap, BorderLayout.EAST);
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showSightingDialog(s[0]);
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                row.setBackground(lightGreen);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                row.setBackground(white);
            }
        });
        return row;
    }

    // ---------------------------------------------------------
    // DIALOGS
    // ---------------------------------------------------------
    private String prettyName(String column) {
        String[] words = column.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof java.util.Date) {
            return new SimpleDateFormat("dd MMM yyyy, HH:mm").format(value);
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? "-" : s;
    }

    private String html(String s) {
        String e = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html><body style='width:280px'>" + e + "</body></html>";
    }
    // Label / value rows for a record (password columns never shown)
    private JPanel createDetailsGrid(String[] cols, Object[] vals,
                                     List<String> hide) {
        JPanel body = new JPanel(new GridBagLayout());
        body.setBackground(white);
        body.setBorder(new EmptyBorder(12, 22, 12, 22));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(7, 0, 7, 12);
        int rows = 0;
        for (int i = 0; i < cols.length; i++) {
            String upper = cols[i].toUpperCase();
            if (upper.contains("PASSWORD") || hide.contains(upper)) {
                continue;
            }
            JLabel key = new JLabel(prettyName(cols[i]));
            key.setFont(new Font("Segoe UI", Font.BOLD, 12));
            key.setForeground(mutedText);
            key.setPreferredSize(new Dimension(150, 20));
            String text;
            if (upper.equals("VERIFICATION_STATUS")) {
                text = statusLabel(normStatus(
                        vals[i] == null ? null : String.valueOf(vals[i])));
            } else if (upper.equals("HAS_CONFLICT") || upper.equals("IS_RARE")
                    || upper.equals("NEEDS_RESCUE")) {
                text = vals[i] == null ? "-"
                        : "1".equals(String.valueOf(vals[i])) ? "Yes" : "No";
            } else {
                text = formatValue(vals[i]);
            }
            JLabel value = new JLabel(html(text));
            value.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            value.setForeground(textColor);
            gbc.gridx = 0;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            body.add(key, gbc);
            gbc.gridx = 1;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            body.add(value, gbc);
            gbc.gridy++;
            rows++;
        }
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        body.add(Box.createGlue(), gbc);
        body.putClientProperty("rows", rows);
        return body;
    }

    private void showRecordDialog(String title, String[] cols, Object[] vals,
                                  Color headerColor) {
        JDialog dialog = new JDialog(this, title, true);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(white);
        JLabel heading = new JLabel(title);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 18));
        heading.setForeground(Color.WHITE);
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(headerColor);
        header.setBorder(new EmptyBorder(16, 22, 16, 22));
        header.add(heading, BorderLayout.CENTER);
        JPanel grid = createDetailsGrid(cols, vals, new ArrayList<String>());
        int rows = (Integer) grid.getClientProperty("rows");
        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(white);
        JButton close = createActionButton("Close", true);
        close.addActionListener(e -> dialog.dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 12));
        footer.setBackground(white);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor));
        footer.add(close);
        root.add(header, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.setSize(520, Math.min(660, 170 + rows * 46));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
    // { String[] columns, Object[] values } or null when no row is found
    private Object[] fetchSingleRow(String sql, String param) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                ResultSetMetaData meta = rs.getMetaData();
                int n = meta.getColumnCount();
                String[] cols = new String[n];
                Object[] vals = new Object[n];
                for (int i = 0; i < n; i++) {
                    cols[i] = meta.getColumnLabel(i + 1);
                    vals[i] = rs.getObject(i + 1);
                }
                return new Object[] { cols, vals };
            }
        }
    }

    private int indexOf(String[] cols, String name) {
        for (int i = 0; i < cols.length; i++) {
            if (cols[i].equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }
    // Full sighting record; officers / admins can also update the status
    private void showSightingDialog(String sightingId) {
        String sql = "SELECT s.*, "
                + "(SELECT u.USERNAME FROM USER_ACCOUNT u WHERE u.USER_ID = s.REPORTED_BY) AS REPORTER_NAME, "
                + "(SELECT u.USERNAME FROM USER_ACCOUNT u WHERE u.USER_ID = s.STATUS_UPDATED_BY) AS UPDATED_BY_NAME "
                + "FROM ANIMAL_SIGHTING s WHERE s.SIGHTING_ID = ?";
        Object[] result;
        try {
            result = fetchSingleRow(sql, sightingId);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Unable to open the record:\n" + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (result == null) {
            JOptionPane.showMessageDialog(this,
                    "This sighting record could not be found.");
            return;
        }
        String[] cols = (String[]) result[0];
        Object[] vals = (Object[]) result[1];
        int speciesIdx = indexOf(cols, "SPECIES_NAME");
        int statusIdx = indexOf(cols, "VERIFICATION_STATUS");
        int notesIdx = indexOf(cols, "OFFICER_NOTES");
        String species = speciesIdx >= 0 && vals[speciesIdx] != null
                ? String.valueOf(vals[speciesIdx]) : "Animal";
        String currentCode = normStatus(statusIdx >= 0 && vals[statusIdx] != null
                ? String.valueOf(vals[statusIdx]) : null);
        String currentNotes = notesIdx >= 0 && vals[notesIdx] != null
                ? String.valueOf(vals[notesIdx]) : "";
        boolean manage = canManage();
        JDialog dialog = new JDialog(this, "Animal Sighting", true);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(white);
        JLabel heading = new JLabel("Animal Sighting - " + species);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 18));
        heading.setForeground(Color.WHITE);
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(darkGreen);
        header.setBorder(new EmptyBorder(16, 22, 16, 22));
        header.add(heading, BorderLayout.CENTER);
        List<String> hide = manage
                ? Arrays.asList("VERIFICATION_STATUS", "OFFICER_NOTES",
                        "REPORTED_BY", "STATUS_UPDATED_BY", "CITY", "DISTRICT")
                : Arrays.asList("REPORTED_BY", "STATUS_UPDATED_BY");
        JPanel grid = createDetailsGrid(cols, vals, hide);
        int rows = (Integer) grid.getClientProperty("rows");
        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(white);
        scroll.setPreferredSize(new Dimension(540,
                Math.min(300, 40 + rows * 40)));
        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(white);
        center.add(scroll, BorderLayout.CENTER);
        final JComboBox<String> statusCombo = new JComboBox<String>(STATUS_LABELS);
        final JTextArea notesArea = new JTextArea(currentNotes, 3, 30);
        int cityIdx = indexOf(cols, "CITY");
        int districtIdx = indexOf(cols, "DISTRICT");
        final JTextField cityField = new JTextField(
                cityIdx >= 0 && vals[cityIdx] != null ? String.valueOf(vals[cityIdx]) : "", 12);
        final JTextField districtField = new JTextField(
                districtIdx >= 0 && vals[districtIdx] != null ? String.valueOf(vals[districtIdx]) : "", 12);
        if (manage) {
            for (int i = 0; i < STATUS_CODES.length; i++) {
                if (STATUS_CODES[i].equals(currentCode)) {
                    statusCombo.setSelectedIndex(i);
                }
            }
            statusCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            notesArea.setLineWrap(true);
            notesArea.setWrapStyleWord(true);
            notesArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            JLabel statusCap = new JLabel("Mark as:");
            statusCap.setFont(new Font("Segoe UI", Font.BOLD, 12));
            statusCap.setForeground(mutedText);
            JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            statusRow.setOpaque(false);
            statusRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            statusRow.add(statusCap);
            statusRow.add(statusCombo);
            JPanel placeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            placeRow.setOpaque(false);
            placeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel cityCap = new JLabel("City:");
            cityCap.setFont(new Font("Segoe UI", Font.BOLD, 12));
            cityCap.setForeground(mutedText);
            JLabel districtCap = new JLabel("District:");
            districtCap.setFont(new Font("Segoe UI", Font.BOLD, 12));
            districtCap.setForeground(mutedText);
            placeRow.add(cityCap);
            placeRow.add(cityField);
            placeRow.add(districtCap);
            placeRow.add(districtField);
            JLabel notesCap = new JLabel(
                    "Preventive measures / notes (required for Under Progress, max 100 characters)");
            notesCap.setFont(new Font("Segoe UI", Font.BOLD, 12));
            notesCap.setForeground(mutedText);
            notesCap.setAlignmentX(Component.LEFT_ALIGNMENT);
            JScrollPane notesScroll = new JScrollPane(notesArea);
            notesScroll.setBorder(BorderFactory.createLineBorder(borderColor));
            notesScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
            JPanel controls = new JPanel();
            controls.setBackground(white);
            controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
            controls.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor),
                    new EmptyBorder(12, 22, 8, 22)));
            controls.add(statusRow);
            controls.add(Box.createVerticalStrut(10));
            controls.add(placeRow);
            controls.add(Box.createVerticalStrut(10));
            controls.add(notesCap);
            controls.add(Box.createVerticalStrut(4));
            controls.add(notesScroll);
            center.add(controls, BorderLayout.SOUTH);
        }
        JButton close = createActionButton("Close", false);
        close.addActionListener(e -> dialog.dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        footer.setBackground(white);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor));
        footer.add(close);
        if (manage) {
            JButton save = createActionButton("Save Status", true);
            save.addActionListener(e -> {
                String code = STATUS_CODES[statusCombo.getSelectedIndex()];
                String notes = notesArea.getText().trim();
                if (notes.length() > 100) {
                    JOptionPane.showMessageDialog(dialog,
                            "Notes cannot exceed 100 characters.");
                    return;
                }
                if (code.equals("PROGRESS") && notes.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog,
                            "Please describe the preventive measures that are going on.");
                    return;
                }
                try (Connection con = DBConnection.getConnection()) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE ANIMAL_SIGHTING SET VERIFICATION_STATUS = ?, "
                            + "OFFICER_NOTES = ?, STATUS_UPDATED_BY = ?, "
                            + "STATUS_UPDATED_DATE = SYSDATE WHERE SIGHTING_ID = ?")) {
                        ps.setString(1, code);
                        if (notes.isEmpty()) {
                            ps.setNull(2, Types.VARCHAR);
                        } else {
                            ps.setString(2, notes);
                        }
                        ps.setInt(3, userId);
                        ps.setString(4, sightingId);
                        ps.executeUpdate();
                    }
                    // city / district decide who gets the alert
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE ANIMAL_SIGHTING SET CITY = ?, DISTRICT = ? "
                            + "WHERE SIGHTING_ID = ?")) {
                        String c = cityField.getText().trim();
                        String d = districtField.getText().trim();
                        if (c.isEmpty()) {
                            ps.setNull(1, Types.VARCHAR);
                        } else {
                            ps.setString(1, c);
                        }
                        if (d.isEmpty()) {
                            ps.setNull(2, Types.VARCHAR);
                        } else {
                            ps.setString(2, d);
                        }
                        ps.setString(3, sightingId);
                        ps.executeUpdate();
                    }
                    // alerts of this sighting follow its status
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE RISK_ALERT SET STATUS = ? WHERE RISK_ID IN "
                            + "(SELECT RISK_ID FROM RISK_RECORD WHERE SIGHTING_ID = ?)")) {
                        ps.setString(1, code.equals("SOLVED") ? "Resolved" : "Active");
                        ps.setString(2, sightingId);
                        ps.executeUpdate();
                    }
                    if (code.equals("SOLVED")) {
                        // finish its rescue case too, and record how it ended
                        List<Integer> openCases = RescueService.openCasesOf(con, sightingId);
                        if (!openCases.isEmpty()) {
                            String[] choices = new String[RescueService.OUTCOME_LABELS.length + 1];
                            System.arraycopy(RescueService.OUTCOME_LABELS, 0, choices, 0,
                                    RescueService.OUTCOME_LABELS.length);
                            choices[choices.length - 1] = "Not specified";
                            JComboBox<String> outcomeBox = new JComboBox<String>(choices);
                            JOptionPane.showMessageDialog(dialog, outcomeBox,
                                    "How did the rescue end?", JOptionPane.QUESTION_MESSAGE);
                            int pick = outcomeBox.getSelectedIndex();
                            String outcome = pick < RescueService.OUTCOMES.length
                                    ? RescueService.OUTCOMES[pick] : null;
                            RescueService.closeBySighting(con, sightingId, outcome, userId);
                        }
                    }
                    dialog.dispose();
                    JOptionPane.showMessageDialog(this,
                            "Sighting marked as " + statusLabel(code) + ".",
                            "Status Updated", JOptionPane.INFORMATION_MESSAGE);
                    refreshDashboard();
                    if (currentTableName.equals("ANIMAL_SIGHTING")
                            || currentTableName.equals("RISK_ALERT")) {
                        loadTableData();
                    }
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(dialog,
                            "Unable to update the status:\n" + ex.getMessage(),
                            "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            footer.add(save);
        }
        root.add(header, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private static class WarningIcon extends JComponent {
        private final Color accent;
        private double phase = 0;
        WarningIcon(Color accent) {
            this.accent = accent;
            setPreferredSize(new Dimension(100, 100));
        }
        void tick() {
            phase += 0.3;
            repaint();
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            double s = 0.92 + 0.08 * Math.sin(phase);
            g2.translate(w / 2.0, h / 2.0);
            g2.scale(s, s);
            g2.translate(-w / 2.0, -h / 2.0);
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

    private JPanel createInfoRow(String caption, String value, Color valueColor) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel cap = new JLabel(caption);
        cap.setFont(new Font("Segoe UI", Font.BOLD, 11));
        cap.setForeground(mutedText);
        cap.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel val = new JLabel(value);
        val.setFont(new Font("Segoe UI", Font.BOLD, 20));
        val.setForeground(valueColor);
        val.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(cap);
        row.add(Box.createVerticalStrut(3));
        row.add(val);
        return row;
    }
    // The RISK ALERT popup: warning symbol, risk level, area, animal, score
    private void showAlertDialog(String areaName, String animalName,
                                 String riskLevel, String date, String score) {
        String area = nz(areaName, "Unknown area");
        String animal = nz(animalName, "Unknown animal");
        String level = levelName(riskLevel);
        Color accent = levelColor(level);
        String message;
        String advice;
        if (level.equals("HIGH")) {
            message = "Danger! Stay away and stay indoors.";
            advice = "Do not go near the area. Keep children and livestock "
                    + "indoors and inform the forest department immediately "
                    + "if you see the animal.";
        } else if (level.equals("MEDIUM")) {
            message = "Be careful in this area.";
            advice = "Avoid travelling alone, stay in groups and keep away "
                    + "from forest edges until the risk goes down.";
        } else if (level.equals("LOW")) {
            message = "Stay aware of your surroundings.";
            advice = "Risk is low right now, but keep watching the area and "
                    + "report any new sightings.";
        } else {
            message = "A new alert has been issued.";
            advice = "Please stay alert and follow the instructions of the "
                    + "forest officers.";
        }
        JDialog dialog = new JDialog(this, "Risk Alert", true);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(white);
        root.setPreferredSize(new Dimension(500, 520));
        JPanel banner = new JPanel(new BorderLayout(20, 0));
        banner.setBackground(accent);
        banner.setBorder(new EmptyBorder(20, 25, 20, 25));
        WarningIcon icon = new WarningIcon(accent);
        JLabel small = new JLabel("RISK ALERT");
        small.setFont(new Font("Segoe UI", Font.BOLD, 12));
        small.setForeground(new Color(255, 255, 255, 215));
        JLabel big = new JLabel(level + " RISK");
        big.setFont(new Font("Segoe UI", Font.BOLD, 32));
        big.setForeground(Color.WHITE);
        JLabel msg = new JLabel(message);
        msg.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        msg.setForeground(Color.WHITE);
        JPanel bannerText = new JPanel();
        bannerText.setOpaque(false);
        bannerText.setLayout(new BoxLayout(bannerText, BoxLayout.Y_AXIS));
        bannerText.add(Box.createVerticalGlue());
        bannerText.add(small);
        bannerText.add(Box.createVerticalStrut(2));
        bannerText.add(big);
        bannerText.add(Box.createVerticalStrut(4));
        bannerText.add(msg);
        bannerText.add(Box.createVerticalGlue());
        banner.add(icon, BorderLayout.WEST);
        banner.add(bannerText, BorderLayout.CENTER);
        JPanel details = new JPanel();
        details.setBackground(white);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setBorder(new EmptyBorder(22, 28, 10, 28));
        JLabel riskCaption = new JLabel("RISK TYPE");
        riskCaption.setFont(new Font("Segoe UI", Font.BOLD, 11));
        riskCaption.setForeground(mutedText);
        riskCaption.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel pill = new JLabel(level, SwingConstants.CENTER);
        pill.setOpaque(true);
        pill.setBackground(accent);
        pill.setForeground(Color.WHITE);
        pill.setFont(new Font("Segoe UI", Font.BOLD, 15));
        pill.setBorder(new EmptyBorder(5, 18, 5, 18));
        pill.setAlignmentX(Component.LEFT_ALIGNMENT);
        details.add(riskCaption);
        details.add(Box.createVerticalStrut(5));
        details.add(pill);
        details.add(Box.createVerticalStrut(16));
        details.add(createInfoRow("AREA", area, textColor));
        details.add(Box.createVerticalStrut(16));
        details.add(createInfoRow("ANIMAL", animal, textColor));
        if (score != null && !score.isEmpty()) {
            details.add(Box.createVerticalStrut(16));
            details.add(createInfoRow("RISK SCORE", score, textColor));
        }
        if (date != null && !date.isEmpty()) {
            details.add(Box.createVerticalStrut(16));
            JLabel issued = new JLabel("Issued on " + date);
            issued.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            issued.setForeground(mutedText);
            issued.setAlignmentX(Component.LEFT_ALIGNMENT);
            details.add(issued);
        }
        JTextArea adviceArea = new JTextArea(advice);
        adviceArea.setEditable(false);
        adviceArea.setLineWrap(true);
        adviceArea.setWrapStyleWord(true);
        adviceArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        adviceArea.setForeground(textColor);
        adviceArea.setBackground(mix(accent, 0.12));
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
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonRow.setOpaque(false);
        buttonRow.add(ok);
        JPanel footer = new JPanel(new BorderLayout(0, 14));
        footer.setBackground(white);
        footer.setBorder(new EmptyBorder(6, 28, 22, 28));
        footer.add(adviceArea, BorderLayout.CENTER);
        footer.add(buttonRow, BorderLayout.SOUTH);
        root.add(banner, BorderLayout.NORTH);
        root.add(details, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.setResizable(false);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        if (level.equals("HIGH")) {
            Toolkit.getDefaultToolkit().beep();
        }
        Timer pulse = new Timer(60, e -> icon.tick());
        pulse.start();
        dialog.setVisible(true);
        pulse.stop();
    }

    // ---------------------------------------------------------
    // REPORTS DASHBOARD (day / week / month wise report)
    // ---------------------------------------------------------

    private JPanel createReportsView() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(background);
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JLabel pageTitle = new JLabel("Reports");
        pageTitle.setFont(new Font("Segoe UI", Font.BOLD, 26));
        pageTitle.setForeground(Color.BLACK);
        root.add(pageTitle, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(12, 0));
        body.setOpaque(false);

        // LEFT COLUMN: the two filter cards at the top with fixed heights
        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(290, 0));

        JPanel leftStack = new JPanel();
        leftStack.setOpaque(false);
        leftStack.setLayout(new BoxLayout(leftStack, BoxLayout.Y_AXIS));

        JPanel typeContent = new JPanel();
        typeContent.setOpaque(false);
        typeContent.setLayout(new BoxLayout(typeContent, BoxLayout.Y_AXIS));
        typeContent.setBorder(new EmptyBorder(8, 3, 5, 3));

        dayWiseRadio = new JRadioButton("Day Wise Report", true);
        weekWiseRadio = new JRadioButton("Week Wise Report");
        monthWiseRadio = new JRadioButton("Month Wise Report");

        ButtonGroup reportGroup = new ButtonGroup();
        reportGroup.add(dayWiseRadio);
        reportGroup.add(weekWiseRadio);
        reportGroup.add(monthWiseRadio);

        styleReportRadio(dayWiseRadio);
        styleReportRadio(weekWiseRadio);
        styleReportRadio(monthWiseRadio);

        typeContent.add(dayWiseRadio);
        typeContent.add(Box.createVerticalStrut(10));
        typeContent.add(weekWiseRadio);
        typeContent.add(Box.createVerticalStrut(10));
        typeContent.add(monthWiseRadio);

        JPanel typeCard = reportCard("Report Type", typeContent);
        typeCard.setPreferredSize(new Dimension(280, 170));
        typeCard.setMinimumSize(new Dimension(280, 170));
        typeCard.setMaximumSize(new Dimension(280, 170));
        typeCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Date controls: labels and date spinners each in their own grid cell
        JPanel dateContent = new JPanel(new GridBagLayout());
        dateContent.setOpaque(false);
        dateContent.setBorder(new EmptyBorder(8, 2, 4, 2));

        GridBagConstraints dc = new GridBagConstraints();
        dc.insets = new Insets(7, 3, 7, 3);
        dc.anchor = GridBagConstraints.WEST;
        dc.fill = GridBagConstraints.HORIZONTAL;

        JLabel fromLabel = new JLabel("From Date:");
        JLabel toLabel = new JLabel("To Date:");
        fromLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        toLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        fromLabel.setForeground(textColor);
        toLabel.setForeground(textColor);

        reportFromDate = createDateSpinner();
        reportToDate = createDateSpinner();
        reportFromDate.setPreferredSize(new Dimension(150, 31));
        reportToDate.setPreferredSize(new Dimension(150, 31));

        dc.gridx = 0;
        dc.gridy = 0;
        dc.weightx = 0;
        dateContent.add(fromLabel, dc);
        dc.gridx = 1;
        dc.weightx = 1;
        dateContent.add(reportFromDate, dc);

        dc.gridx = 0;
        dc.gridy = 1;
        dc.weightx = 0;
        dateContent.add(toLabel, dc);
        dc.gridx = 1;
        dc.weightx = 1;
        dateContent.add(reportToDate, dc);

        JButton generate = createActionButton("Generate Report", true);
        generate.setPreferredSize(new Dimension(160, 42));
        generate.setMinimumSize(new Dimension(160, 42));
        generate.addActionListener(e -> generateDashboardReport(true));

        dc.gridx = 0;
        dc.gridy = 2;
        dc.gridwidth = 2;
        dc.weightx = 1;
        dc.anchor = GridBagConstraints.CENTER;
        dc.fill = GridBagConstraints.NONE;
        dateContent.add(generate, dc);

        JPanel dateCard = reportCard("Select Date", dateContent);
        dateCard.setPreferredSize(new Dimension(280, 190));
        dateCard.setMinimumSize(new Dimension(280, 190));
        dateCard.setMaximumSize(new Dimension(280, 190));
        dateCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        leftStack.add(typeCard);
        leftStack.add(Box.createVerticalStrut(12));
        leftStack.add(dateCard);
        left.add(leftStack, BorderLayout.NORTH);

        // RIGHT COLUMN
        JPanel right = new JPanel(new BorderLayout(0, 12));
        right.setOpaque(false);

        // Report Summary heading above the four summary tiles
        JPanel statsContent = new JPanel(new GridLayout(1, 4, 10, 0));
        statsContent.setOpaque(false);

        reportSightingsValue = addReportStat(statsContent, "Total Sightings", "0",
                new Color(235, 246, 232), darkGreen);
        reportConflictsValue = addReportStat(statsContent, "Total Conflicts", "0",
                new Color(232, 244, 248), new Color(45, 105, 125));
        reportHighAlertsValue = addReportStat(statsContent, "High Risk Alerts", "0",
                new Color(250, 243, 232), new Color(180, 105, 35));
        reportRescueValue = addReportStat(statsContent, "Rescue Actions", "0",
                new Color(244, 238, 248), new Color(110, 70, 140));

        JPanel summaryCard = reportCard("Report Summary", statsContent);
        summaryCard.setPreferredSize(new Dimension(0, 135));

        // REPORT TABLE
        JPanel tableCard = new JPanel(new BorderLayout(8, 10));
        tableCard.setBackground(Color.WHITE);
        tableCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor),
                new EmptyBorder(10, 12, 12, 12)));

        JPanel tableTop = new JPanel(new BorderLayout(8, 0));
        tableTop.setOpaque(false);

        reportSummaryLabel = new JLabel("Day Wise Report");
        reportSummaryLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        reportSummaryLabel.setForeground(textColor);
        tableTop.add(reportSummaryLabel, BorderLayout.WEST);

        JButton excelButton = createActionButton("Export Excel", false);
        JButton pdfButton = createActionButton("Print / PDF", false);
        excelButton.addActionListener(e -> exportReportsExcel());
        pdfButton.addActionListener(e -> exportReportsPdf());

        JPanel exportButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        exportButtons.setOpaque(false);
        exportButtons.add(excelButton);
        exportButtons.add(pdfButton);
        tableTop.add(exportButtons, BorderLayout.EAST);

        tableCard.add(tableTop, BorderLayout.NORTH);

        reportsTable = new JTable(new DefaultTableModel(
                new Object[][] {},
                new String[] { "Sighting ID", "Sighting Date/Time", "Date", "Area",
                    "Animal", "Sighting Count", "Conflict Count", "Risk Level",
                    "Alerts Generated" }) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        });

        reportsTable.setRowHeight(34);
        reportsTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        reportsTable.setForeground(textColor);
        reportsTable.setBackground(Color.WHITE);
        reportsTable.setGridColor(new Color(225, 232, 225));
        reportsTable.setShowGrid(true);
        reportsTable.setFillsViewportHeight(true);
        reportsTable.setSelectionBackground(new Color(222, 239, 226));
        reportsTable.setSelectionForeground(textColor);
        reportsTable.setAutoCreateRowSorter(true);
        reportsTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        reportsTable.getTableHeader().setBackground(darkGreen);
        reportsTable.getTableHeader().setForeground(Color.WHITE);
        reportsTable.getTableHeader().setPreferredSize(new Dimension(0, 37));
        reportsTable.getTableHeader().setReorderingAllowed(false);
        reportsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        for (int col = 0; col < reportsTable.getColumnCount(); col++) {
            reportsTable.getColumnModel().getColumn(col).setCellRenderer(centerRenderer);
        }

        reportsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateReportDetails();
            }
        });

        JScrollPane tableScroll = new JScrollPane(reportsTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(borderColor));
        tableScroll.getViewport().setBackground(Color.WHITE);
        tableCard.add(tableScroll, BorderLayout.CENTER);

        // BOTTOM: three equal detail cards
        reportDetailsLabel = reportTextPanel("Select a report row to view details.");
        reportAlertLabel = reportTextPanel("No alert information available.");

        JPanel detailsCard = reportCard(
                "Report Details", reportDetailBox(reportDetailsLabel));
        JPanel summaryDetailsCard = reportCard(
                "Summary", reportSummaryBodyPanel());
        JPanel alertCard = reportCard(
                "Alert Message", reportDetailBox(reportAlertLabel));

        JPanel bottomCards = new JPanel(new GridLayout(1, 3, 12, 0));
        bottomCards.setOpaque(false);
        bottomCards.add(detailsCard);
        bottomCards.add(summaryDetailsCard);
        bottomCards.add(alertCard);
        bottomCards.setPreferredSize(new Dimension(0, 175));

        JPanel rightCenter = new JPanel(new BorderLayout(0, 12));
        rightCenter.setOpaque(false);
        rightCenter.add(summaryCard, BorderLayout.NORTH);
        rightCenter.add(tableCard, BorderLayout.CENTER);
        rightCenter.add(bottomCards, BorderLayout.SOUTH);

        right.add(rightCenter, BorderLayout.CENTER);

        body.add(left, BorderLayout.WEST);
        body.add(right, BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);

        dayWiseRadio.addActionListener(e -> {
            currentReportPeriod = "Day Wise Report";
            updateDateRangeForPeriod();
        });
        weekWiseRadio.addActionListener(e -> {
            currentReportPeriod = "Week Wise Report";
            updateDateRangeForPeriod();
        });
        monthWiseRadio.addActionListener(e -> {
            currentReportPeriod = "Month Wise Report";
            updateDateRangeForPeriod();
        });

        return root;
    }

    // A card keeps its heading and its content in separate layout regions
    private JPanel reportCard(String heading, Component content) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor),
                new EmptyBorder(10, 12, 12, 12)));

        JLabel headingLabel = new JLabel(heading);
        headingLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        headingLabel.setForeground(darkGreen);

        card.add(headingLabel, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private void styleReportRadio(JRadioButton radio) {
        radio.setOpaque(false);
        radio.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        radio.setForeground(textColor);
        radio.setFocusPainted(false);
        radio.setAlignmentX(Component.LEFT_ALIGNMENT);
        radio.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
    }

    private JSpinner createDateSpinner() {
        JSpinner spinner = new JSpinner(new SpinnerDateModel(
                new java.util.Date(), null, null, java.util.Calendar.DAY_OF_MONTH));
        spinner.setEditor(new JSpinner.DateEditor(spinner, "dd-MM-yyyy"));
        spinner.setPreferredSize(new Dimension(145, 30));
        spinner.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        return spinner;
    }

    private JLabel addReportStat(JPanel parent, String name, String value,
                                 Color tileColor, Color foreground) {
        JPanel tile = new JPanel(new BorderLayout(2, 5));
        tile.setBackground(tileColor);
        tile.setBorder(new EmptyBorder(12, 8, 12, 8));

        JLabel nameLabel = new JLabel(name, SwingConstants.CENTER);
        nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        nameLabel.setForeground(foreground);

        JLabel valueLabel = new JLabel(value, SwingConstants.CENTER);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 23));
        valueLabel.setForeground(foreground);

        tile.add(nameLabel, BorderLayout.NORTH);
        tile.add(valueLabel, BorderLayout.CENTER);
        parent.add(tile);
        return valueLabel;
    }

    private JLabel reportTextPanel(String text) {
        JLabel label = new JLabel("<html><div style='width:270px'>"
                + text.replace("\n", "<br>") + "</div></html>");
        label.setVerticalAlignment(SwingConstants.TOP);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        label.setForeground(textColor);
        return label;
    }

    private JPanel reportSummaryBodyPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(2, 2, 2, 2));

        reportSummaryBody = new JLabel(
                "<html>Total Sightings: 0<br>Conflicts: 0<br>"
                + "High Risk Alerts: 0<br>Rescue Actions: 0</html>");
        reportSummaryBody.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        reportSummaryBody.setForeground(textColor);

        panel.add(reportSummaryBody, BorderLayout.NORTH);
        return panel;
    }

    private JPanel reportDetailBox(JLabel label) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(2, 2, 2, 2));
        panel.add(label, BorderLayout.NORTH);
        return panel;
    }

    // Day = just the To date, Week = 7 days ending on the To date,
    // Month = from the 1st of the To date's month
    private void updateDateRangeForPeriod() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        java.util.Date endDate = (java.util.Date) reportToDate.getValue();
        calendar.setTime(endDate);

        if ("Week Wise Report".equals(currentReportPeriod)) {
            calendar.add(java.util.Calendar.DAY_OF_MONTH, -6);
        } else if ("Month Wise Report".equals(currentReportPeriod)) {
            calendar.set(java.util.Calendar.DAY_OF_MONTH, 1);
        }
        reportFromDate.setValue(calendar.getTime());
    }

    // The spinner holds a date with a time of day. Oracle DATE columns hold a time
    // too, so the report compares TRUNC(column) with these midnight dates.
    private java.sql.Date spinnerSqlDate(JSpinner spinner) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime((java.util.Date) spinner.getValue());
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return new java.sql.Date(cal.getTimeInMillis());
    }

    // saveHistory = true when the person presses Generate Report (a row is added
    // to the REPORT table); false when the Reports page is just opened.
    private void generateDashboardReport(boolean saveHistory) {
        if (reportsTable == null) {
            return;
        }
        java.sql.Date from = spinnerSqlDate(reportFromDate);
        java.sql.Date to = spinnerSqlDate(reportToDate);
        if (from.after(to)) {
            JOptionPane.showMessageDialog(this, "From Date cannot be after To Date.");
            return;
        }

        SimpleDateFormat longFormat = new SimpleDateFormat("dd-MM-yyyy");
        reportSummaryLabel.setText(currentReportPeriod + " - "
                + longFormat.format(from) + " to " + longFormat.format(to));

        DefaultTableModel model = (DefaultTableModel) reportsTable.getModel();
        model.setRowCount(0);

        String countSql = "SELECT COUNT(*) AS SIGHTINGS, "
                + "NVL(SUM(HAS_CONFLICT), 0) AS CONFLICTS "
                + "FROM ANIMAL_SIGHTING "
                + "WHERE TRUNC(SIGHTING_DATE) BETWEEN ? AND ?";
        String alertSql = "SELECT COUNT(*) FROM RISK_ALERT "
                + "WHERE TRUNC(ALERT_DATE) BETWEEN ? AND ? "
                + "AND UPPER(RISK_LEVEL) = 'HIGH'";
        String rescueSql = "SELECT COUNT(*) FROM RESCUE_CASE "
                + "WHERE TRUNC(REPORTED_DATE) BETWEEN ? AND ?";
        String rowsSql = "SELECT TRUNC(s.SIGHTING_DATE) AS DAY_VALUE, s.AREA_NAME, "
                + "s.SPECIES_NAME, "
                + "COUNT(DISTINCT s.SIGHTING_ID) AS SIGHTING_COUNT, "
                + "COUNT(DISTINCT CASE WHEN s.HAS_CONFLICT = 1 "
                + "THEN s.SIGHTING_ID END) AS CONFLICT_COUNT, "
                + "CASE MAX(CASE UPPER(rr.RISK_LEVEL) WHEN 'HIGH' THEN 3 "
                + "WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 1 ELSE 0 END) "
                + "WHEN 3 THEN 'High' WHEN 2 THEN 'Medium' ELSE 'Low' END AS RISK_LEVEL, "
                + "(SELECT COUNT(*) FROM RISK_ALERT ra "
                + "JOIN RISK_RECORD r2 ON ra.RISK_ID = r2.RISK_ID "
                + "JOIN ANIMAL_SIGHTING s2 ON r2.SIGHTING_ID = s2.SIGHTING_ID "
                + "WHERE s2.AREA_NAME = s.AREA_NAME AND s2.SPECIES_NAME = s.SPECIES_NAME "
                + "AND TRUNC(ra.ALERT_DATE) BETWEEN ? AND ?) AS ALERTS "
                + "FROM ANIMAL_SIGHTING s "
                + "LEFT JOIN RISK_RECORD rr ON rr.SIGHTING_ID = s.SIGHTING_ID "
                + "WHERE TRUNC(s.SIGHTING_DATE) BETWEEN ? AND ? "
                + "GROUP BY TRUNC(s.SIGHTING_DATE), s.AREA_NAME, s.SPECIES_NAME "
                + "ORDER BY TRUNC(s.SIGHTING_DATE) DESC, s.AREA_NAME, s.SPECIES_NAME";

        try (Connection con = DBConnection.getConnection()) {

            try (PreparedStatement ps = con.prepareStatement(countSql)) {
                ps.setDate(1, from);
                ps.setDate(2, to);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        reportSightingsValue.setText(rs.getString("SIGHTINGS"));
                        reportConflictsValue.setText(rs.getString("CONFLICTS"));
                    }
                }
            }

            try (PreparedStatement ps = con.prepareStatement(alertSql)) {
                ps.setDate(1, from);
                ps.setDate(2, to);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        reportHighAlertsValue.setText(rs.getString(1));
                    }
                }
            }

            // If the rescue table cannot be read, show "-" instead of a wrong 0
            String rescueText = "-";
            try (PreparedStatement ps = con.prepareStatement(rescueSql)) {
                ps.setDate(1, from);
                ps.setDate(2, to);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        rescueText = rs.getString(1);
                    }
                }
            } catch (SQLException ex) {
                // RESCUE_CASE / REPORTED_DATE not found in this database
            }
            reportRescueValue.setText(rescueText);

            try (PreparedStatement ps = con.prepareStatement(rowsSql)) {
                ps.setDate(1, from);
                ps.setDate(2, to);
                ps.setDate(3, from);
                ps.setDate(4, to);
                try (ResultSet rs = ps.executeQuery()) {
                    int i = 1;
                    String generated = new SimpleDateFormat("dd-MM-yyyy HH:mm")
                            .format(new java.util.Date());
                    while (rs.next()) {
                        model.addRow(new Object[] {
                                String.format("RPT%04d", i++), generated,
                                longFormat.format(rs.getDate("DAY_VALUE")),
                                rs.getString("AREA_NAME"), rs.getString("SPECIES_NAME"),
                                rs.getInt("SIGHTING_COUNT"), rs.getInt("CONFLICT_COUNT"),
                                rs.getString("RISK_LEVEL"), rs.getInt("ALERTS") });
                    }
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Unable to generate report:\n" + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (saveHistory) {
            // REPORT_DETAILS holds only 25 characters, e.g. "01-11-26 to 17-11-26"
            SimpleDateFormat shortFormat = new SimpleDateFormat("dd-MM-yy");
            try {
                saveReport(currentReportPeriod, shortFormat.format(from)
                        + " to " + shortFormat.format(to));
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this,
                        "The report is shown, but it could not be saved "
                        + "to the report history:\n" + ex.getMessage(),
                        "Report Not Saved", JOptionPane.WARNING_MESSAGE);
            }
        }
        updateReportDetails();
    }

    private void updateReportDetails() {
        if (reportsTable == null || reportDetailsLabel == null) {
            return;
        }
        if (reportSummaryBody != null) {
            reportSummaryBody.setText("<html>• Total Sightings: "
                    + reportSightingsValue.getText()
                    + "<br>• Total Conflicts: " + reportConflictsValue.getText()
                    + "<br>• High Risk Alerts: " + reportHighAlertsValue.getText()
                    + "<br>• Rescue Actions: " + reportRescueValue.getText()
                    + "</html>");
        }

        int row = reportsTable.getSelectedRow();
        if (row < 0 && reportsTable.getRowCount() > 0) {
            reportsTable.setRowSelectionInterval(0, 0);
            return;
        }
        if (row < 0) {
            reportDetailsLabel.setText("<html><b>Report Details</b><br>"
                    + "No records found for selected dates.</html>");
            reportAlertLabel.setText("<html><b>Alert Message</b><br>"
                    + "No alert information available.</html>");
            return;
        }

        row = reportsTable.convertRowIndexToModel(row);
        DefaultTableModel m = (DefaultTableModel) reportsTable.getModel();
        String area = String.valueOf(m.getValueAt(row, 3));
        String animal = String.valueOf(m.getValueAt(row, 4));
        String risk = String.valueOf(m.getValueAt(row, 7));

        reportDetailsLabel.setText("<html><b>Report Details</b><br>Report ID: "
                + m.getValueAt(row, 0)
                + "<br>Generated: " + m.getValueAt(row, 1)
                + "<br>Date: " + m.getValueAt(row, 2)
                + "<br>Area: " + area + "<br>Animal: " + animal + "</html>");

        String alert = risk.equals("High")
                ? "High risk of human-animal conflict reported in " + area
                        + ". Please stay alert and avoid movement in forest areas."
                : "Risk level is " + risk + " for " + animal + " in " + area
                        + ". Follow local forest department guidance.";
        reportAlertLabel.setText("<html><b>Alert Message</b><br>" + alert
                + "<br><br>- Forest Department</html>");
    }

    private void exportReportsExcel() {
        if (reportsTable == null || reportsTable.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Generate a report first.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("Animal_Safety_Report.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try (FileWriter writer = new FileWriter(chooser.getSelectedFile())) {
            DefaultTableModel m = (DefaultTableModel) reportsTable.getModel();
            for (int c = 0; c < m.getColumnCount(); c++) {
                if (c > 0) {
                    writer.append(',');
                }
                writer.append(csv(m.getColumnName(c)));
            }
            writer.append("\r\n");
            for (int r = 0; r < m.getRowCount(); r++) {
                for (int c = 0; c < m.getColumnCount(); c++) {
                    if (c > 0) {
                        writer.append(',');
                    }
                    writer.append(csv(String.valueOf(m.getValueAt(r, c))));
                }
                writer.append("\r\n");
            }
            JOptionPane.showMessageDialog(this,
                    "Excel-compatible CSV report exported successfully.");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage());
        }
    }

    private String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void exportReportsPdf() {
        if (reportsTable == null || reportsTable.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Generate a report first.");
            return;
        }
        try {
            reportsTable.print(JTable.PrintMode.FIT_WIDTH,
                    new java.text.MessageFormat("Human-Animal Safety Report"),
                    new java.text.MessageFormat("Page {0}"));
        } catch (java.awt.print.PrinterException ex) {
            JOptionPane.showMessageDialog(this,
                    "PDF/print operation failed: " + ex.getMessage());
        }
    }

    // ---------------------------------------------------------
    // RECORDS VIEW (Sightings, Risk Records, Risk Alerts, Reports)
    // ---------------------------------------------------------
    private JPanel createTableView() {
        JPanel view = new JPanel(new BorderLayout());
        view.setBackground(background);
        view.setBorder(new EmptyBorder(25, 30, 25, 30));
        JButton refreshButton = createActionButton("Refresh", true);
        refreshButton.addActionListener(e -> loadTableData());
        JButton backButton = createActionButton("Back to Dashboard", false);
        backButton.addActionListener(e -> {
            cardLayout.show(cards, "DASHBOARD");
            refreshDashboard();
        });
        generateButton = createActionButton("Generate Report", true);
        generateButton.addActionListener(e -> generateReport());
        generateButton.setVisible(false);
        JPanel header = createHeader("Records", "",
                backButton, generateButton, refreshButton);
        dataTitleLabel = (JLabel) header.getClientProperty("titleLabel");
        dataSubLabel = (JLabel) header.getClientProperty("subLabel");
        view.add(header, BorderLayout.NORTH);
        dataTable = new JTable();
        dataTable.setAutoCreateRowSorter(true);
        styleTable(dataTable);
        dataTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedRecord();
                }
            }
        });
        dataCountLabel = new JLabel("0 record(s)");
        dataCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        dataCountLabel.setForeground(textColor);
        dataHintLabel = new JLabel("Double-click a row to open its full record");
        dataHintLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        dataHintLabel.setForeground(mutedText);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(dataCountLabel, BorderLayout.WEST);
        top.add(dataHintLabel, BorderLayout.EAST);
        JScrollPane scrollPane = new JScrollPane(dataTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(borderColor));
        scrollPane.getViewport().setBackground(Color.WHITE);
        JPanel card = createCard();
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(15, 15, 15, 15));
        card.add(top, BorderLayout.NORTH);
        card.add(scrollPane, BorderLayout.CENTER);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(background);
        wrapper.setBorder(new EmptyBorder(25, 0, 0, 0));
        wrapper.add(card, BorderLayout.CENTER);
        view.add(wrapper, BorderLayout.CENTER);
        return view;
    }

    private void showTableView(String tableName, String title) {
        boolean allowed = false;
        for (String table : ALLOWED_TABLES) {
            if (table.equals(tableName)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            JOptionPane.showMessageDialog(this, "Invalid table selection.");
            return;
        }
        currentTableName = tableName;
        currentTableTitle = title;
        dataTitleLabel.setText(title);
        String sub;
        String hint;
        if (tableName.equals("RISK_ALERT")) {
            sub = canManage() ? "All risk alerts. Open one to see its warning."
                    : "Alerts for your area, city and district. Open one to see its warning.";
            hint = "Double-click an alert to open the warning popup";
        } else if (tableName.equals("RISK_RECORD")) {
            sub = "Risk assessments created from animal sightings";
            hint = "Double-click a row to open the risk record";
        } else if (tableName.equals("REPORT")) {
            sub = canManage() ? "Generated reports. Use Generate Report to create a new one."
                    : "Generated reports";
            hint = "Double-click a row to open the report entry";
        } else if (tableName.equals("ANIMAL_SIGHTING")) {
            sub = "All reported animal sightings";
            hint = canManage()
                    ? "Double-click a row to view it and update its status"
                    : "Double-click a row to view it";
        } else {
            sub = "All records from " + tableName.replace("_", " ").toLowerCase();
            hint = "Double-click a row to open its full record";
        }
        dataSubLabel.setText(sub);
        dataHintLabel.setText(hint);
        generateButton.setVisible(tableName.equals("REPORT") && canManage());
        cardLayout.show(cards, "TABLE");
        loadTableData();
    }

    private void loadTableData() {
        final String tableName = currentTableName;
        String order = "";
        if (tableName.equals("RISK_ALERT")) {
            order = " ORDER BY ALERT_DATE DESC";
        } else if (tableName.equals("REPORT")) {
            order = " ORDER BY GENERATED_DATE DESC";
        } else if (tableName.equals("ANIMAL_SIGHTING")) {
            order = " ORDER BY SIGHTING_DATE DESC";
        }
        final String orderBy = order;
        final String baseSql = "SELECT * FROM " + tableName + orderBy;
        SwingWorker<DefaultTableModel, Void> worker =
                new SwingWorker<DefaultTableModel, Void>() {
            @Override
            protected DefaultTableModel doInBackground() throws Exception {
                try (Connection con = DBConnection.getConnection()) {
                    String sql = baseSql;
                    if (tableName.equals("RISK_ALERT") && !canManage()) {
                        // a general user only sees the alerts meant for them
                        List<String> ids = AlertRouting.visibleAlertIds(
                                AlertRouting.load(con, userId, false));
                        StringBuilder in = new StringBuilder();
                        for (String id : ids) {
                            in.append(in.length() == 0 ? "" : ",")
                                    .append("'").append(id).append("'");
                        }
                        sql = "SELECT * FROM RISK_ALERT WHERE "
                                + (ids.isEmpty() ? "1 = 0"
                                        : "TO_CHAR(ALERT_ID) IN (" + in + ")")
                                + orderBy;
                    }
                    try (Statement st = con.createStatement();
                         ResultSet rs = st.executeQuery(sql)) {
                        return createTableModel(rs);
                    }
                }
            }
            @Override
            protected void done() {
                try {
                    DefaultTableModel model = get();
                    dataTable.setModel(model);
                    styleTable(dataTable);
                    hideSensitiveColumns(dataTable);
                    applyRenderers(dataTable, tableName);
                    dataCountLabel.setText(model.getRowCount() + " record(s)");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(CommonDashboard.this,
                            "Unable to open " + tableName + ":\n" + ex.getMessage(),
                            "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
    // Double-click on a row
    private void openSelectedRecord() {
        int viewRow = dataTable.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        int modelRow = dataTable.convertRowIndexToModel(viewRow);
        DefaultTableModel model = (DefaultTableModel) dataTable.getModel();
        int count = model.getColumnCount();
        String[] cols = new String[count];
        Object[] vals = new Object[count];
        for (int i = 0; i < count; i++) {
            cols[i] = model.getColumnName(i);
            vals[i] = model.getValueAt(modelRow, i);
        }
        if (currentTableName.equals("RISK_ALERT")) {
            int areaIdx = indexOf(cols, "AREA_NAME");
            int speciesIdx = indexOf(cols, "SPECIES_NAME");
            int levelIdx = indexOf(cols, "RISK_LEVEL");
            int dateIdx = indexOf(cols, "ALERT_DATE");
            int idIdx = indexOf(cols, "ALERT_ID");
            int scoreIdx = indexOf(cols, "RISK_SCORE");
            if (areaIdx >= 0 && speciesIdx >= 0 && levelIdx >= 0) {
                String date = dateIdx >= 0 && vals[dateIdx] != null
                        ? new SimpleDateFormat("dd MMM yyyy").format(vals[dateIdx])
                        : "";
                String rawId = idIdx >= 0 && vals[idIdx] != null
                        ? String.valueOf(vals[idIdx]) : null;
                String score = scoreIdx >= 0 && vals[scoreIdx] != null
                        ? String.valueOf(vals[scoreIdx]) : null;
                if (rawId != null) {
                    markSeen(rawId);
                }
                showAlertDialog(
                        vals[areaIdx] == null ? null : String.valueOf(vals[areaIdx]),
                        vals[speciesIdx] == null ? null : String.valueOf(vals[speciesIdx]),
                        vals[levelIdx] == null ? null : String.valueOf(vals[levelIdx]),
                        date, score);
                renderAlertList();
                return;
            }
        }
        if (currentTableName.equals("ANIMAL_SIGHTING")) {
            int idIdx = indexOf(cols, "SIGHTING_ID");
            if (idIdx >= 0 && vals[idIdx] != null) {
                showSightingDialog(String.valueOf(vals[idIdx]));
                return;
            }
        }
        // A saved report only keeps a 25-character summary, so reopening it
        // rebuilds the full report from the latest data
        if (currentTableName.equals("REPORT")) {
            int typeIdx = indexOf(cols, "REPORT_TYPE");
            int detailsIdx = indexOf(cols, "REPORT_DETAILS");
            int dateIdx = indexOf(cols, "GENERATED_DATE");
            int byIdx = indexOf(cols, "GENERATED_BY");
            String type = typeIdx >= 0 && vals[typeIdx] != null
                    ? String.valueOf(vals[typeIdx]).trim() : "";
            String details = detailsIdx >= 0 && vals[detailsIdx] != null
                    ? String.valueOf(vals[detailsIdx]).trim() : "";
            boolean known = Arrays.asList(REPORT_TYPES).contains(type);
            boolean areaOk = !type.equals("Area Report") || !details.isEmpty();
            if (known && areaOk) {
                String date = dateIdx >= 0 && vals[dateIdx] != null
                        ? new SimpleDateFormat("dd MMM yyyy, HH:mm")
                                .format(vals[dateIdx]) : "-";
                String by = byIdx >= 0 && vals[byIdx] != null
                        ? String.valueOf(vals[byIdx]) : null;
                openSavedReport(type, details, date, by);
                return;
            }
        }
        Color header = darkGreen;
        if (currentTableName.equals("RISK_RECORD")) {
            int levelIdx = indexOf(cols, "RISK_LEVEL");
            if (levelIdx >= 0 && vals[levelIdx] != null) {
                header = levelColor(String.valueOf(vals[levelIdx]));
            }
        }
        showRecordDialog(currentTableTitle + " - Record", cols, vals, header);
    }
    // Colours / friendly text for important columns
    private class CellStyler extends DefaultTableCellRenderer {
        private final String kind;
        CellStyler(String kind) {
            this.kind = kind;
        }
        @Override
        public Component getTableCellRendererComponent(JTable table,
                Object value, boolean selected, boolean focus, int row, int col) {
            String raw = value == null ? "" : String.valueOf(value);
            String text = raw;
            Color color = null;
            if (kind.equals("STATUS_CODE")) {
                String code = normStatus(raw);
                text = statusLabel(code);
                color = statusColor(code);
            } else if (kind.equals("LEVEL")) {
                color = levelColor(raw);
            } else if (kind.equals("YESNO")) {
                text = raw.equals("1") ? "Yes" : raw.equals("0") ? "No" : raw;
            } else if (kind.equals("ALERT_STATUS")) {
                color = raw.equalsIgnoreCase("Resolved") ? mutedText : riskLow;
            }
            super.getTableCellRendererComponent(table, text, selected, focus, row, col);
            if (color != null) {
                setFont(getFont().deriveFont(Font.BOLD));
                if (!selected) {
                    setForeground(color);
                }
            }
            return this;
        }
    }

    private void applyRenderers(JTable table, String tableName) {
        TableColumnModel cm = table.getColumnModel();
        for (int i = 0; i < cm.getColumnCount(); i++) {
            String name = String.valueOf(cm.getColumn(i).getHeaderValue()).toUpperCase();
            if (name.equals("VERIFICATION_STATUS")) {
                cm.getColumn(i).setCellRenderer(new CellStyler("STATUS_CODE"));
            } else if (name.equals("RISK_LEVEL")) {
                cm.getColumn(i).setCellRenderer(new CellStyler("LEVEL"));
            } else if (name.equals("IS_RARE") || name.equals("NEEDS_RESCUE")
                    || name.equals("HAS_CONFLICT")) {
                cm.getColumn(i).setCellRenderer(new CellStyler("YESNO"));
            } else if (name.equals("STATUS") && tableName.equals("RISK_ALERT")) {
                cm.getColumn(i).setCellRenderer(new CellStyler("ALERT_STATUS"));
            }
        }
    }

    // ---------------------------------------------------------
    // REPORTS (generate, show and save in REPORT table)
    // ---------------------------------------------------------
    private void generateReport() {
        String type = (String) JOptionPane.showInputDialog(this,
                "Choose the report to generate:", "Generate Report",
                JOptionPane.QUESTION_MESSAGE, null, REPORT_TYPES, REPORT_TYPES[0]);
        if (type == null) {
            return;
        }
        String area = null;
        if (type.equals("Area Report")) {
            area = JOptionPane.showInputDialog(this, "Enter the area name:",
                    "Area Report", JOptionPane.QUESTION_MESSAGE);
            if (area == null || area.trim().isEmpty()) {
                return;
            }
            area = area.trim();
        }
        final String reportType = type;
        final String reportArea = area;
        SwingWorker<String[], Void> worker = new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() throws Exception {
                try (Connection con = DBConnection.getConnection()) {
                    return buildReport(con, reportType, reportArea);
                }
            }
            @Override
            protected void done() {
                try {
                    String[] result = get();   // { short details, full text }
                    saveReport(reportType, result[0]);
                    if (currentTableName.equals("REPORT")) {
                        loadTableData();
                    }
                    showReportDialog(reportType, result[1], "Generated by "
                            + username + " on "
                            + new SimpleDateFormat("dd MMM yyyy, HH:mm")
                                    .format(new java.util.Date())
                            + "  (saved to Reports)");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(CommonDashboard.this,
                            "Unable to generate the report:\n" + ex.getMessage(),
                            "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
    // Opens a saved report in the same full view as when it was generated
    private void openSavedReport(String type, String details,
                                 String date, String generatedBy) {
        final String area = type.equals("Area Report") ? details : null;
        SwingWorker<String[], Void> worker = new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() throws Exception {
                try (Connection con = DBConnection.getConnection()) {
                    String[] report = buildReport(con, type, area);
                    String who = "user " + generatedBy;
                    if (generatedBy != null) {
                        try (PreparedStatement ps = con.prepareStatement(
                                "SELECT USERNAME FROM USER_ACCOUNT WHERE USER_ID = ?")) {
                            ps.setString(1, generatedBy);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    who = rs.getString(1);
                                }
                            }
                        }
                    } else {
                        who = "unknown";
                    }
                    return new String[] { report[1], who };
                }
            }
            @Override
            protected void done() {
                try {
                    String[] result = get();
                    showReportDialog(type, result[0], "Generated by " + result[1]
                            + " on " + date
                            + "  (figures shown are the latest data)");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(CommonDashboard.this,
                            "Unable to open the report:\n" + ex.getMessage(),
                            "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
    // REPORT_DETAILS is only VARCHAR2(25), so only a short summary is saved
    private void saveReport(String type, String details) throws SQLException {
        String sql = "INSERT INTO REPORT (REPORT_TYPE, GENERATED_BY, "
                + "GENERATED_DATE, REPORT_DETAILS) VALUES (?, ?, SYSDATE, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setInt(2, userId);
            ps.setString(3, cut(details, 25));
            ps.executeUpdate();
        } catch (SQLException ex) {
            if (ex.getErrorCode() != 1400) {   // 1400 = REPORT_ID is NULL
                throw ex;
            }
            // REPORT_ID does not fill itself: use the next number
            String sql2 = "INSERT INTO REPORT (REPORT_ID, REPORT_TYPE, "
                    + "GENERATED_BY, GENERATED_DATE, REPORT_DETAILS) VALUES "
                    + "((SELECT NVL(MAX(REPORT_ID), 0) + 1 FROM REPORT), ?, ?, SYSDATE, ?)";
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql2)) {
                ps.setString(1, type);
                ps.setInt(2, userId);
                ps.setString(3, cut(details, 25));
                ps.executeUpdate();
            }
        }
    }

    private void showReportDialog(String type, String text, String subtitle) {
        JDialog dialog = new JDialog(this, type, true);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(white);
        JLabel heading = new JLabel(type);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 20));
        heading.setForeground(Color.WHITE);
        JLabel sub = new JLabel(subtitle);
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(new Color(200, 225, 205));
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(darkGreen);
        header.setBorder(new EmptyBorder(16, 22, 16, 22));
        header.add(heading);
        header.add(Box.createVerticalStrut(4));
        header.add(sub);
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setForeground(textColor);
        area.setBorder(new EmptyBorder(14, 18, 14, 18));
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(620, 400));
        JButton close = createActionButton("Close", true);
        close.addActionListener(e -> dialog.dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 12));
        footer.setBackground(white);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor));
        footer.add(close);
        root.add(header, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
    // returns { short details (max 25 chars), full report text }
    private String[] buildReport(Connection con, String type, String area)
            throws SQLException {
        StringBuilder sb = new StringBuilder();
        String shortText;
        if (type.equals("Sightings Summary")) {
            int total = getCount(con, "ANIMAL_SIGHTING");
            Map<String, Integer> st = getStatusDistribution(con);
            sb.append("Total sightings : ").append(total).append("\n");
            sb.append("With conflict   : ").append(scalar(con,
                    "SELECT COUNT(*) FROM ANIMAL_SIGHTING WHERE HAS_CONFLICT = 1")).append("\n\n");
            sb.append("BY STATUS\n");
            for (int i = 0; i < STATUS_CODES.length; i++) {
                sb.append(String.format("  %-16s %d%n", STATUS_LABELS[i],
                        st.get(STATUS_CODES[i])));
            }
            sb.append("\nBY CATEGORY\n");
            appendRows(sb, queryRows(con,
                    "SELECT ANIMAL_CATEGORY, COUNT(*) FROM ANIMAL_SIGHTING "
                    + "GROUP BY ANIMAL_CATEGORY ORDER BY 2 DESC", 2), "  %-26s %s%n");
            sb.append("\nTOP SPECIES\n");
            appendRows(sb, queryRows(con,
                    "SELECT SPECIES_NAME, COUNT(*) FROM ANIMAL_SIGHTING "
                    + "GROUP BY SPECIES_NAME ORDER BY 2 DESC FETCH FIRST 5 ROWS ONLY", 2),
                    "  %-26s %s%n");
            sb.append("\nTOP AREAS\n");
            appendRows(sb, queryRows(con,
                    "SELECT AREA_NAME, COUNT(*) FROM ANIMAL_SIGHTING "
                    + "GROUP BY AREA_NAME ORDER BY 2 DESC FETCH FIRST 5 ROWS ONLY", 2),
                    "  %-26s %s%n");
            shortText = total + " sightings, " + st.get("PENDING") + " pending";
        } else if (type.equals("Risk Summary")) {
            Map<String, Integer> lv = getLevelDistribution(con, "RISK_RECORD");
            sb.append("Total risk records : ").append(getCount(con, "RISK_RECORD")).append("\n");
            sb.append("Average risk score : ").append(scalarText(con,
                    "SELECT NVL(ROUND(AVG(RISK_SCORE), 2), 0) FROM RISK_RECORD")).append("\n\n");
            sb.append("BY LEVEL\n");
            sb.append(String.format("  %-10s %d%n", "High", lv.get("HIGH")));
            sb.append(String.format("  %-10s %d%n", "Medium", lv.get("MEDIUM")));
            sb.append(String.format("  %-10s %d%n", "Low", lv.get("LOW")));
            sb.append("\nHIGHEST SCORES\n");
            appendRows(sb, queryRows(con,
                    "SELECT AREA_NAME, SPECIES_NAME, RISK_SCORE, RISK_LEVEL FROM RISK_RECORD "
                    + "ORDER BY RISK_SCORE DESC FETCH FIRST 5 ROWS ONLY", 4),
                    "  %-16s %-18s %-6s %s%n");
            sb.append("\nAREAS WITH MOST RISK RECORDS\n");
            appendRows(sb, queryRows(con,
                    "SELECT AREA_NAME, COUNT(*) FROM RISK_RECORD "
                    + "GROUP BY AREA_NAME ORDER BY 2 DESC FETCH FIRST 5 ROWS ONLY", 2),
                    "  %-26s %s%n");
            shortText = "H:" + lv.get("HIGH") + " M:" + lv.get("MEDIUM")
                    + " L:" + lv.get("LOW");
        } else if (type.equals("Alert Summary")) {
            Map<String, Integer> lv = getLevelDistribution(con, "RISK_ALERT");
            int active = scalar(con, "SELECT COUNT(*) FROM RISK_ALERT "
                    + "WHERE UPPER(NVL(STATUS, 'Active')) <> 'RESOLVED'");
            int resolved = scalar(con, "SELECT COUNT(*) FROM RISK_ALERT "
                    + "WHERE UPPER(NVL(STATUS, 'Active')) = 'RESOLVED'");
            sb.append("Active alerts   : ").append(active).append("\n");
            sb.append("Resolved alerts : ").append(resolved).append("\n\n");
            sb.append("ACTIVE ALERTS BY LEVEL\n");
            sb.append(String.format("  %-10s %d%n", "High", lv.get("HIGH")));
            sb.append(String.format("  %-10s %d%n", "Medium", lv.get("MEDIUM")));
            sb.append(String.format("  %-10s %d%n", "Low", lv.get("LOW")));
            sb.append("\nLATEST ALERTS\n");
            appendRows(sb, queryRows(con,
                    "SELECT AREA_NAME, SPECIES_NAME, RISK_LEVEL, "
                    + "TO_CHAR(ALERT_DATE, 'DD Mon YYYY') FROM RISK_ALERT "
                    + "ORDER BY ALERT_DATE DESC FETCH FIRST 5 ROWS ONLY", 4),
                    "  %-16s %-18s %-8s %s%n");
            shortText = active + " active, " + resolved + " resolved";
        } else if (type.equals("Rescue Cases")) {
            List<String[]> rows = queryRows(con,
                    "SELECT SIGHTING_ID, SPECIES_NAME, AREA_NAME, ANIMAL_CONDITION, "
                    + "ANIMAL_COUNT, CONTACT_PHONE FROM ANIMAL_SIGHTING "
                    + "WHERE NEEDS_RESCUE = 1 "
                    + "AND UPPER(NVL(VERIFICATION_STATUS, 'PENDING')) NOT LIKE 'SOLV%' "
                    + "ORDER BY SIGHTING_DATE DESC FETCH FIRST 20 ROWS ONLY", 6);
            int rare = scalar(con,
                    "SELECT COUNT(*) FROM ANIMAL_SIGHTING WHERE IS_RARE = 1");
            sb.append("Open rescue cases   : ").append(rows.size()).append("\n");
            sb.append("Rare species seen   : ").append(rare).append("\n\n");
            sb.append("OPEN RESCUE CASES (latest 20)\n");
            sb.append(String.format("  %-5s %-14s %-14s %-15s %-4s %s%n",
                    "ID", "Animal", "Area", "Condition", "No.", "Phone"));
            appendRows(sb, rows, "  %-5s %-14s %-14s %-15s %-4s %s%n");
            shortText = rows.size() + " rescue, " + rare + " rare";
        } else if (type.equals("Rescue Report")) {
            String[] rescue = RescueService.report(con);
            sb.append(rescue[1]);
            shortText = rescue[0];
        } else {
            AreaRiskService.AreaHistory h = AreaRiskService.loadHistory(area);
            AreaRiskService.Assessment a = AreaRiskService.assess(
                    h, null, Double.NaN, false);
            sb.append("Area       : ").append(area).append("\n");
            sb.append("Risk level : ").append(h.items.isEmpty() ? "NO DATA" : a.level)
                    .append("  (score ").append(a.score).append(")\n");
            sb.append("Sightings  : ").append(h.totalSightings).append("\n");
            sb.append("Conflicts  : ").append(h.totalConflicts).append("\n\n");
            sb.append("ANIMALS FOUND HERE (last 180 days)\n");
            if (h.items.isEmpty()) {
                sb.append("  No sightings recorded.\n");
            }
            SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy");
            for (AreaRiskService.SpeciesHistory s : h.items) {
                sb.append(String.format("  %-22s seen %-3d conflicts %-3d last %s%n",
                        s.species, s.count, s.conflicts,
                        s.lastSeen == null ? "-" : fmt.format(s.lastSeen)));
            }
            sb.append("\nWHY THIS LEVEL\n");
            for (String r : a.reasons) {
                sb.append("  - ").append(r).append("\n");
            }
            shortText = area;
        }
        return new String[] { cut(shortText, 25), sb.toString() };
    }

    private void appendRows(StringBuilder sb, List<String[]> rows, String format) {
        if (rows.isEmpty()) {
            sb.append("  (none)\n");
            return;
        }
        for (String[] r : rows) {
            sb.append(String.format(format, (Object[]) r));
        }
    }

    private List<String[]> queryRows(Connection con, String sql, int columns)
            throws SQLException {
        List<String[]> list = new ArrayList<String[]>();
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String[] row = new String[columns];
                for (int i = 0; i < columns; i++) {
                    String v = rs.getString(i + 1);
                    row[i] = v == null ? "-" : cut(v, 18);
                }
                list.add(row);
            }
        }
        return list;
    }

    private int scalar(Connection con, String sql) throws SQLException {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private String scalarText(Connection con, String sql) throws SQLException {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : "0";
        }
    }

    // ---------------------------------------------------------
    // USERS VIEW (admin)
    // ---------------------------------------------------------
    private JPanel createUsersView() {
        JPanel view = new JPanel(new BorderLayout());
        view.setBackground(background);
        view.setBorder(new EmptyBorder(25, 30, 25, 30));
        JButton refreshButton = createActionButton("Refresh", true);
        refreshButton.addActionListener(e -> loadUsers());
        JButton backButton = createActionButton("Back to Dashboard", false);
        backButton.addActionListener(e -> {
            cardLayout.show(cards, "DASHBOARD");
            refreshDashboard();
        });
        view.add(createHeader("Manage Users", "All registered user accounts",
                backButton, refreshButton), BorderLayout.NORTH);
        usersTable = new JTable();
        usersTable.setAutoCreateRowSorter(true);
        styleTable(usersTable);
        usersCountLabel = new JLabel("0 user(s)");
        usersCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        usersCountLabel.setForeground(textColor);
        JScrollPane scrollPane = new JScrollPane(usersTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(borderColor));
        scrollPane.getViewport().setBackground(Color.WHITE);
        JPanel card = createCard();
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(15, 15, 15, 15));
        card.add(usersCountLabel, BorderLayout.NORTH);
        card.add(scrollPane, BorderLayout.CENTER);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(background);
        wrapper.setBorder(new EmptyBorder(25, 0, 0, 0));
        wrapper.add(card, BorderLayout.CENTER);
        view.add(wrapper, BorderLayout.CENTER);
        return view;
    }

    private void loadUsers() {
        SwingWorker<DefaultTableModel, Void> worker =
                new SwingWorker<DefaultTableModel, Void>() {
            @Override
            protected DefaultTableModel doInBackground() throws Exception {
                try (Connection con = DBConnection.getConnection();
                     Statement st = con.createStatement();
                     ResultSet rs = st.executeQuery("SELECT * FROM USER_ACCOUNT")) {
                    return createTableModel(rs);
                }
            }
            @Override
            protected void done() {
                try {
                    DefaultTableModel model = get();
                    usersTable.setModel(model);
                    styleTable(usersTable);
                    hideSensitiveColumns(usersTable);
                    usersCountLabel.setText(model.getRowCount() + " user(s)");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(CommonDashboard.this,
                            "Unable to load users:\n" + ex.getMessage(),
                            "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void hideSensitiveColumns(JTable table) {
        TableColumnModel columnModel = table.getColumnModel();
        for (int i = columnModel.getColumnCount() - 1; i >= 0; i--) {
            String name = String.valueOf(
                    columnModel.getColumn(i).getHeaderValue()).toUpperCase();
            if (name.contains("PASSWORD")) {
                columnModel.removeColumn(columnModel.getColumn(i));
            }
        }
    }

    // ---------------------------------------------------------
    // PROFILE VIEW
    // ---------------------------------------------------------
    private JPanel createProfileView() {
        JPanel view = new JPanel(new BorderLayout());
        view.setBackground(background);
        view.setBorder(new EmptyBorder(25, 30, 25, 30));
        view.add(createHeader("My Profile", "View and manage your account"),
                BorderLayout.NORTH);
        JPanel card = createCard();
        card.setLayout(new BorderLayout(0, 18));
        card.setBorder(new EmptyBorder(25, 28, 22, 28));
        card.setPreferredSize(new Dimension(700, 470));
        profileAvatar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(green);
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                String initial = username == null || username.isEmpty()
                        ? "?" : username.substring(0, 1).toUpperCase();
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 32));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(initial,
                        (getWidth() - fm.stringWidth(initial)) / 2,
                        (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        };
        profileAvatar.setOpaque(false);
        profileAvatar.setPreferredSize(new Dimension(76, 76));
        profileNameLabel = new JLabel(username);
        profileNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        profileNameLabel.setForeground(darkGreen);
        profileNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        profileRoleLabel = new JLabel(role);
        profileRoleLabel.setOpaque(true);
        profileRoleLabel.setBackground(lightGreen);
        profileRoleLabel.setForeground(darkGreen);
        profileRoleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        profileRoleLabel.setBorder(new EmptyBorder(4, 12, 4, 12));
        profileRoleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel names = new JPanel();
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        names.add(Box.createVerticalGlue());
        names.add(profileNameLabel);
        names.add(Box.createVerticalStrut(6));
        names.add(profileRoleLabel);
        names.add(Box.createVerticalGlue());
        JPanel top = new JPanel(new BorderLayout(18, 0));
        top.setOpaque(false);
        top.add(profileAvatar, BorderLayout.WEST);
        top.add(names, BorderLayout.CENTER);
        profileDetailsPanel = new JPanel(new GridBagLayout());
        profileDetailsPanel.setOpaque(false);
        JScrollPane detailsScroll = new JScrollPane(profileDetailsPanel);
        detailsScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor));
        detailsScroll.setOpaque(false);
        detailsScroll.getViewport().setOpaque(false);
        JButton updateButton = createActionButton("Update Profile", true);
        updateButton.addActionListener(e -> showUpdateProfileDialog());
        JButton passwordButton = createActionButton("Change Password", false);
        passwordButton.addActionListener(e -> showChangePasswordDialog());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        buttons.setOpaque(false);
        buttons.add(updateButton);
        buttons.add(passwordButton);
        card.add(top, BorderLayout.NORTH);
        card.add(detailsScroll, BorderLayout.CENTER);
        card.add(buttons, BorderLayout.SOUTH);
        JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        holder.setBackground(background);
        holder.setBorder(new EmptyBorder(25, 0, 0, 0));
        holder.add(card);
        view.add(holder, BorderLayout.CENTER);
        return view;
    }

    private List<Object[]> fetchProfile(Connection con) throws SQLException {
        List<Object[]> list = new ArrayList<Object[]>();
        String sql = "SELECT * FROM USER_ACCOUNT WHERE " + USER_ID_COLUMN + " = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return list;
                }
                ResultSetMetaData meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    String name = meta.getColumnLabel(i).toUpperCase();
                    if (name.contains("PASSWORD")) {
                        continue;
                    }
                    int type = meta.getColumnType(i);
                    boolean isDate = type == Types.DATE
                            || type == Types.TIMESTAMP
                            || type == Types.TIME
                            || type == Types.TIMESTAMP_WITH_TIMEZONE;
                    boolean editable = !name.equals(USER_ID_COLUMN)
                            && !name.contains("ROLE")
                            && !name.contains("STATUS")
                            && !name.contains("CREATED")
                            && !isDate;
                    list.add(new Object[] { name, rs.getObject(i), editable });
                }
            }
        }
        return list;
    }

    private void loadProfile() {
        profileNameLabel.setText(username);
        profileAvatar.repaint();
        SwingWorker<List<Object[]>, Void> worker =
                new SwingWorker<List<Object[]>, Void>() {
            @Override
            protected List<Object[]> doInBackground() throws Exception {
                try (Connection con = DBConnection.getConnection()) {
                    return fetchProfile(con);
                }
            }
            @Override
            protected void done() {
                try {
                    List<Object[]> rows = get();
                    profileDetailsPanel.removeAll();
                    GridBagConstraints gbc = new GridBagConstraints();
                    gbc.gridy = 0;
                    gbc.anchor = GridBagConstraints.NORTHWEST;
                    gbc.insets = new Insets(9, 2, 9, 12);
                    for (Object[] row : rows) {
                        JLabel key = new JLabel(prettyName((String) row[0]));
                        key.setFont(new Font("Segoe UI", Font.BOLD, 12));
                        key.setForeground(mutedText);
                        key.setPreferredSize(new Dimension(170, 20));
                        JLabel value = new JLabel(formatValue(row[1]));
                        value.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                        value.setForeground(textColor);
                        gbc.gridx = 0;
                        gbc.weightx = 0;
                        gbc.fill = GridBagConstraints.NONE;
                        profileDetailsPanel.add(key, gbc);
                        gbc.gridx = 1;
                        gbc.weightx = 1;
                        gbc.fill = GridBagConstraints.HORIZONTAL;
                        profileDetailsPanel.add(value, gbc);
                        gbc.gridy++;
                    }
                    if (rows.isEmpty()) {
                        JLabel none = new JLabel("Profile details not found.");
                        none.setForeground(mutedText);
                        gbc.gridx = 0;
                        profileDetailsPanel.add(none, gbc);
                        gbc.gridy++;
                    }
                    gbc.gridx = 0;
                    gbc.gridwidth = 2;
                    gbc.weighty = 1;
                    gbc.fill = GridBagConstraints.BOTH;
                    profileDetailsPanel.add(Box.createGlue(), gbc);
                    profileDetailsPanel.revalidate();
                    profileDetailsPanel.repaint();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(CommonDashboard.this,
                            "Unable to load profile:\n" + ex.getMessage(),
                            "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void showUpdateProfileDialog() {
        List<Object[]> rows;
        try (Connection con = DBConnection.getConnection()) {
            rows = fetchProfile(con);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Unable to load profile:\n" + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JDialog dialog = new JDialog(this, "Update Profile", true);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(white);
        form.setBorder(new EmptyBorder(20, 25, 10, 25));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(7, 0, 7, 12);
        final Map<String, JTextField> fields =
                new LinkedHashMap<String, JTextField>();
        for (Object[] row : rows) {
            if (!((Boolean) row[2])) {
                continue;
            }
            String column = (String) row[0];
            JLabel label = new JLabel(prettyName(column));
            label.setFont(new Font("Segoe UI", Font.BOLD, 12));
            label.setForeground(mutedText);
            label.setPreferredSize(new Dimension(130, 20));
            JTextField field = new JTextField(
                    row[1] == null ? "" : String.valueOf(row[1]), 22);
            field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(borderColor),
                    new EmptyBorder(6, 8, 6, 8)));
            gbc.gridx = 0;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            form.add(label, gbc);
            gbc.gridx = 1;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            form.add(field, gbc);
            gbc.gridy++;
            fields.put(column, field);
        }
        if (fields.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "There are no editable profile details.");
            return;
        }
        JButton save = createActionButton("Save Changes", true);
        JButton cancel = createActionButton("Cancel", false);
        cancel.addActionListener(e -> dialog.dispose());
        save.addActionListener(e -> {
            for (Map.Entry<String, JTextField> entry : fields.entrySet()) {
                String column = entry.getKey();
                String value = entry.getValue().getText().trim();
                if (column.contains("NAME") && value.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog,
                            prettyName(column) + " cannot be empty.");
                    return;
                }
                if (column.contains("EMAIL") && !value.isEmpty()
                        && !value.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) {
                    JOptionPane.showMessageDialog(dialog,
                            "Please enter a valid email address.");
                    return;
                }
                if ((column.contains("PHONE") || column.contains("MOBILE"))
                        && !value.isEmpty()
                        && !value.matches("^[+0-9 \\-]{7,15}$")) {
                    JOptionPane.showMessageDialog(dialog,
                            "Please enter a valid phone number.");
                    return;
                }
            }
            StringBuilder sql = new StringBuilder("UPDATE USER_ACCOUNT SET ");
            int n = 0;
            for (String column : fields.keySet()) {
                if (n++ > 0) {
                    sql.append(", ");
                }
                sql.append(column).append(" = ?");
            }
            sql.append(" WHERE ").append(USER_ID_COLUMN).append(" = ?");
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql.toString())) {
                int index = 1;
                String newUsername = null;
                for (Map.Entry<String, JTextField> entry : fields.entrySet()) {
                    String value = entry.getValue().getText().trim();
                    ps.setString(index++, value.isEmpty() ? null : value);
                    if (entry.getKey().equals("USERNAME")
                            || entry.getKey().equals("USER_NAME")) {
                        newUsername = value;
                    }
                }
                ps.setInt(index, userId);
                ps.executeUpdate();
                if (newUsername != null && !newUsername.isEmpty()) {
                    username = newUsername;
                    sidebarUserLabel.setText(username);
                }
                dialog.dispose();
                loadProfile();
                JOptionPane.showMessageDialog(this,
                        "Your profile has been updated.",
                        "Profile Updated", JOptionPane.INFORMATION_MESSAGE);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(dialog,
                        "Unable to update profile:\n" + ex.getMessage(),
                        "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        buttons.setBackground(white);
        buttons.add(cancel);
        buttons.add(save);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(white);
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void showChangePasswordDialog() {
        JDialog dialog = new JDialog(this, "Change Password", true);
        JPasswordField currentField = new JPasswordField(20);
        JPasswordField newField = new JPasswordField(20);
        JPasswordField confirmField = new JPasswordField(20);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(white);
        form.setBorder(new EmptyBorder(20, 25, 10, 25));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(7, 0, 7, 12);
        String[] captions = { "Current Password", "New Password", "Confirm Password" };
        JPasswordField[] inputs = { currentField, newField, confirmField };
        for (int i = 0; i < captions.length; i++) {
            JLabel label = new JLabel(captions[i]);
            label.setFont(new Font("Segoe UI", Font.BOLD, 12));
            label.setForeground(mutedText);
            label.setPreferredSize(new Dimension(130, 20));
            inputs[i].setFont(new Font("Segoe UI", Font.PLAIN, 14));
            inputs[i].setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(borderColor),
                    new EmptyBorder(6, 8, 6, 8)));
            gbc.gridx = 0;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            form.add(label, gbc);
            gbc.gridx = 1;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            form.add(inputs[i], gbc);
            gbc.gridy++;
        }
        JLabel rule = new JLabel("Use at least 6 characters (max 25).");
        rule.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        rule.setForeground(mutedText);
        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(rule, gbc);
        JButton save = createActionButton("Change Password", true);
        JButton cancel = createActionButton("Cancel", false);
        cancel.addActionListener(e -> dialog.dispose());
        save.addActionListener(e -> {
            String current = new String(currentField.getPassword());
            String newPass = new String(newField.getPassword());
            String confirm = new String(confirmField.getPassword());
            if (current.isEmpty() || newPass.isEmpty() || confirm.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please fill in all the fields.");
                return;
            }
            if (newPass.length() < 6) {
                JOptionPane.showMessageDialog(dialog,
                        "The new password must be at least 6 characters.");
                return;
            }
            if (newPass.length() > 25) {
                JOptionPane.showMessageDialog(dialog,
                        "The new password cannot exceed 25 characters.");
                return;
            }
            if (!newPass.equals(confirm)) {
                JOptionPane.showMessageDialog(dialog,
                        "New password and confirm password do not match.");
                return;
            }
            if (newPass.equals(current)) {
                JOptionPane.showMessageDialog(dialog,
                        "The new password must be different from the current one.");
                return;
            }
            try (Connection con = DBConnection.getConnection()) {
                String passwordColumn = findPasswordColumn(con);
                if (passwordColumn == null) {
                    JOptionPane.showMessageDialog(dialog,
                            "No password column was found in USER_ACCOUNT.");
                    return;
                }
                String stored = null;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT " + passwordColumn + " FROM USER_ACCOUNT WHERE "
                                + USER_ID_COLUMN + " = ?")) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            stored = rs.getString(1);
                        }
                    }
                }
                if (stored == null || !stored.equals(current)) {
                    JOptionPane.showMessageDialog(dialog,
                            "The current password is incorrect.",
                            "Change Password", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE USER_ACCOUNT SET " + passwordColumn
                                + " = ? WHERE " + USER_ID_COLUMN + " = ?")) {
                    ps.setString(1, newPass);
                    ps.setInt(2, userId);
                    ps.executeUpdate();
                }
                dialog.dispose();
                JOptionPane.showMessageDialog(this,
                        "Your password has been changed successfully.",
                        "Password Changed", JOptionPane.INFORMATION_MESSAGE);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(dialog,
                        "Unable to change password:\n" + ex.getMessage(),
                        "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        buttons.setBackground(white);
        buttons.add(cancel);
        buttons.add(save);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(white);
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private String findPasswordColumn(Connection con) throws SQLException {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT * FROM USER_ACCOUNT WHERE 1 = 0")) {
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                String name = meta.getColumnLabel(i);
                if (name.toUpperCase().contains("PASSWORD")) {
                    return name;
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------
    // TABLE DESIGN
    // ---------------------------------------------------------
    private void styleTable(JTable table) {
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setRowHeight(27);
        table.setBackground(Color.WHITE);
        table.setForeground(textColor);
        table.setGridColor(borderColor);
        table.setSelectionBackground(lightGreen);
        table.setSelectionForeground(darkGreen);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setBackground(lightGreen);
        table.getTableHeader().setForeground(darkGreen);
        table.getTableHeader().setReorderingAllowed(false);
    }

    private DefaultTableModel createTableModel(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        String[] columns = new String[columnCount];
        for (int i = 0; i < columnCount; i++) {
            columns[i] = meta.getColumnLabel(i + 1);
        }
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        while (rs.next()) {
            Object[] row = new Object[columnCount];
            for (int i = 0; i < columnCount; i++) {
                row[i] = rs.getObject(i + 1);
            }
            model.addRow(row);
        }
        return model;
    }

    // ---------------------------------------------------------
    // REFRESH DASHBOARD DATA
    // ---------------------------------------------------------
    private void refreshDashboard() {
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private int a;
            private int b;
            private int c;
            private int d;
            private Map<String, Integer> riskData = new LinkedHashMap<String, Integer>();
            private Map<String, Integer> speciesData = new LinkedHashMap<String, Integer>();
            private Map<String, Integer> trendData = new LinkedHashMap<String, Integer>();
            private Map<String, Integer> areaData = new LinkedHashMap<String, Integer>();
            private Map<String, Integer> alertLevels = new LinkedHashMap<String, Integer>();
            private Map<String, Integer> statusData = new LinkedHashMap<String, Integer>();
            private List<String[]> recentSightings = new ArrayList<String[]>();
            private List<String[]> toReview = new ArrayList<String[]>();
            private List<String[]> latestAlerts = new ArrayList<String[]>();
            private List<String[]> sosCases = new ArrayList<String[]>();
            @Override
            protected Void doInBackground() throws Exception {
                try (Connection con = DBConnection.getConnection()) {
                    if (isAdmin()) {
                        a = getCount(con, "USER_ACCOUNT");
                        b = getCount(con, "ANIMAL_SIGHTING");
                        c = getCount(con, "RISK_RECORD");
                        d = getCount(con, "RISK_ALERT");
                        riskData = getLevelDistribution(con, "RISK_RECORD");
                        speciesData = getTopCounts(con, "ANIMAL_SIGHTING", "SPECIES_NAME", 6);
                        recentSightings = getRecentSightings(con);
                        latestAlerts = getLatestAlerts(con);
                    } else if (isOfficer()) {
                        a = getCount(con, "ANIMAL_SIGHTING");
                        d = getCount(con, "RISK_ALERT");
                        riskData = getLevelDistribution(con, "RISK_RECORD");
                        c = riskData.get("HIGH");
                        statusData = getStatusDistribution(con);
                        b = statusData.get("PENDING");
                        trendData = getSightingTrend(con);
                        areaData = getTopCounts(con, "RISK_ALERT", "AREA_NAME", 6);
                        toReview = getSightingsToReview(con);
                        latestAlerts = getLatestAlerts(con);
                    } else {
                        latestAlerts = getLatestAlerts(con);
                        alertLevels = AlertRouting.countByLevel(routedAlerts);
                    }
                    if (canManage()) {
                        sosCases = RescueService.openSos(con);
                    }
                }
                return null;
            }
            @Override
            protected void done() {
                try {
                    get();
                    if (isAdmin()) {
                        statA.setText(String.valueOf(a));
                        statB.setText(String.valueOf(b));
                        statC.setText(String.valueOf(c));
                        statD.setText(String.valueOf(d));
                        riskDonut.setData(riskData);
                        speciesChart.setData(speciesData);
                        currentSightings = recentSightings;
                        renderSightingList();
                    } else if (isOfficer()) {
                        statA.setText(String.valueOf(a));
                        statB.setText(String.valueOf(b));
                        statC.setText(String.valueOf(c));
                        statD.setText(String.valueOf(d));
                        riskBar.setData(riskData);
                        trendChart.setData(trendData);
                        areaChart.setData(areaData);
                        statusChart.setData(statusData);
                        currentReviews = toReview;
                        renderReviewList();
                    } else {
                        statA.setText(String.valueOf(alertLevels.get("HIGH")));
                        statB.setText(String.valueOf(alertLevels.get("MEDIUM")));
                        statC.setText(String.valueOf(alertLevels.get("LOW")));
                    }
                    currentAlerts = latestAlerts;
                    renderAlertList();
                    // once after login: warning popup for the most serious new alert
                    if (!startupPopupDone) {
                        startupPopupDone = true;
                        showStartupAlert();
                    }
                    showNewSosPopups(sosCases);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(CommonDashboard.this,
                            "Unable to load dashboard data:\n" + ex.getMessage(),
                            "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // ---------------------------------------------------------
    // DATABASE QUERIES
    // ---------------------------------------------------------
    private int getCount(Connection con, String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }
    // HIGH / MEDIUM / LOW counts (resolved alerts are not counted)
    private Map<String, Integer> getLevelDistribution(Connection con,
            String tableName) throws SQLException {
        Map<String, Integer> data = new LinkedHashMap<String, Integer>();
        data.put("HIGH", 0);
        data.put("MEDIUM", 0);
        data.put("LOW", 0);
        String where = tableName.equals("RISK_ALERT")
                ? "WHERE UPPER(NVL(STATUS, 'Active')) <> 'RESOLVED' " : "";
        String sql = "SELECT UPPER(TRIM(RISK_LEVEL)) AS LEVEL_NAME, "
                + "COUNT(*) AS TOTAL FROM " + tableName + " " + where
                + "GROUP BY UPPER(TRIM(RISK_LEVEL))";
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String level = rs.getString("LEVEL_NAME");
                int total = rs.getInt("TOTAL");
                if (level == null) {
                    continue;
                }
                if (level.contains("HIGH")) {
                    data.put("HIGH", data.get("HIGH") + total);
                } else if (level.contains("MEDIUM")) {
                    data.put("MEDIUM", data.get("MEDIUM") + total);
                } else if (level.contains("LOW")) {
                    data.put("LOW", data.get("LOW") + total);
                }
            }
        }
        return data;
    }

    private Map<String, Integer> getStatusDistribution(Connection con)
            throws SQLException {
        Map<String, Integer> data = new LinkedHashMap<String, Integer>();
        for (String code : STATUS_CODES) {
            data.put(code, 0);
        }
        String sql = "SELECT VERIFICATION_STATUS AS S, COUNT(*) AS TOTAL "
                + "FROM ANIMAL_SIGHTING GROUP BY VERIFICATION_STATUS";
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String code = normStatus(rs.getString("S"));
                data.put(code, data.get(code) + rs.getInt("TOTAL"));
            }
        }
        return data;
    }

    private Map<String, Integer> getTopCounts(Connection con, String tableName,
            String columnName, int limit) throws SQLException {
        Map<String, Integer> data = new LinkedHashMap<String, Integer>();
        String sql = "SELECT " + columnName + " AS NAME_VALUE, COUNT(*) AS TOTAL "
                + "FROM " + tableName + " "
                + "WHERE " + columnName + " IS NOT NULL "
                + "GROUP BY " + columnName + " "
                + "ORDER BY TOTAL DESC "
                + "FETCH FIRST " + limit + " ROWS ONLY";
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                data.put(rs.getString("NAME_VALUE"), rs.getInt("TOTAL"));
            }
        }
        return data;
    }

    private Map<String, Integer> getSightingTrend(Connection con)
            throws SQLException {
        String sql = "SELECT TRUNC(SIGHTING_DATE) AS DAY_VALUE, COUNT(*) AS TOTAL "
                + "FROM ANIMAL_SIGHTING "
                + "WHERE SIGHTING_DATE IS NOT NULL "
                + "GROUP BY TRUNC(SIGHTING_DATE) "
                + "ORDER BY DAY_VALUE DESC "
                + "FETCH FIRST 7 ROWS ONLY";
        List<String> labels = new ArrayList<String>();
        List<Integer> values = new ArrayList<Integer>();
        SimpleDateFormat format = new SimpleDateFormat("dd MMM");
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Timestamp day = rs.getTimestamp("DAY_VALUE");
                labels.add(day == null ? "?" : format.format(day));
                values.add(rs.getInt("TOTAL"));
            }
        }
        Map<String, Integer> data = new LinkedHashMap<String, Integer>();
        for (int i = labels.size() - 1; i >= 0; i--) {
            data.put(labels.get(i), values.get(i));
        }
        return data;
    }
    // Alerts this person may see, already routed by area / city / district:
    // { key, area, species, level, date, rawId, score, unverified, kind, sightingId }
    // Up to 6 active alerts, then up to 2 recent "Area clear" notices.
    private List<String[]> getLatestAlerts(Connection con) throws SQLException {
        List<String[]> all = AlertRouting.load(con, userId, canManage());
        routedAlerts = all;
        List<String[]> list = new ArrayList<String[]>();
        int active = 0;
        int clear = 0;
        for (String[] a : all) {
            if (AlertRouting.KIND_CLEAR.equals(a[AlertRouting.KIND])) {
                if (clear++ < 2) {
                    list.add(a);
                }
            } else if (active++ < 6) {
                list.add(a);
            }
        }
        return list;
    }

    private List<String[]> getRecentSightings(Connection con) throws SQLException {
        List<String[]> list = new ArrayList<String[]>();
        String sql = "SELECT SIGHTING_ID, SPECIES_NAME FROM ANIMAL_SIGHTING "
                + "ORDER BY SIGHTING_DATE DESC, SIGHTING_ID DESC "
                + "FETCH FIRST 8 ROWS ONLY";
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new String[] {
                        rs.getString("SIGHTING_ID"),
                        rs.getString("SPECIES_NAME") });
            }
        }
        return list;
    }
    // Sightings that still need the officer: { id, species, area, status, rare, rescue }
    private List<String[]> getSightingsToReview(Connection con) throws SQLException {
        List<String[]> list = new ArrayList<String[]>();
        String sql = "SELECT SIGHTING_ID, SPECIES_NAME, AREA_NAME, "
                + "VERIFICATION_STATUS, IS_RARE, NEEDS_RESCUE FROM ANIMAL_SIGHTING "
                + "WHERE UPPER(NVL(VERIFICATION_STATUS, 'PENDING')) NOT LIKE 'SOLV%' "
                + "ORDER BY CASE WHEN UPPER(NVL(VERIFICATION_STATUS, 'PENDING')) = 'PENDING' "
                + "THEN 0 ELSE 1 END, NEEDS_RESCUE DESC, IS_RARE DESC, "
                + "SIGHTING_DATE DESC "
                + "FETCH FIRST 8 ROWS ONLY";
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new String[] {
                        rs.getString("SIGHTING_ID"),
                        rs.getString("SPECIES_NAME"),
                        rs.getString("AREA_NAME"),
                        rs.getString("VERIFICATION_STATUS"),
                        rs.getString("IS_RARE"),
                        rs.getString("NEEDS_RESCUE") });
            }
        }
        return list;
    }

    // ---------------------------------------------------------
    // CUSTOM CHARTS
    // ---------------------------------------------------------
    class SimpleChart extends JPanel {
        private final ChartType type;
        boolean statusColors = false;
        private Map<String, Integer> data = new LinkedHashMap<String, Integer>();
        private final Color[] palette = {
                new Color(53, 125, 85),
                new Color(70, 130, 180),
                new Color(235, 174, 72),
                new Color(150, 110, 170),
                new Color(204, 76, 76),
                new Color(100, 160, 160)
        };
        SimpleChart(ChartType type) {
            this.type = type;
            setBackground(Color.WHITE);
        }
        void setData(Map<String, Integer> newData) {
            data = new LinkedHashMap<String, Integer>(newData);
            repaint();
        }
        private Color colorFor(String key, int index) {
            if (statusColors) {
                return statusColor(normStatus(key));
            }
            String k = key == null ? "" : key.toUpperCase();
            if (k.equals("HIGH")) {
                return riskHigh;
            } else if (k.equals("MEDIUM")) {
                return riskMedium;
            } else if (k.equals("LOW")) {
                return riskLow;
            }
            return palette[index % palette.length];
        }
        private String pretty(String key) {
            if (key == null || key.isEmpty()) {
                return "-";
            }
            if (statusColors) {
                return statusLabel(normStatus(key));
            }
            if (key.equals("HIGH") || key.equals("MEDIUM") || key.equals("LOW")) {
                return key.charAt(0) + key.substring(1).toLowerCase();
            }
            return key;
        }
        private int total() {
            int t = 0;
            for (int v : data.values()) {
                t += v;
            }
            return t;
        }
        private int maxValue() {
            int m = 0;
            for (int v : data.values()) {
                m = Math.max(m, v);
            }
            return m;
        }
        private int gridMax() {
            int m = maxValue();
            int g = ((m + 3) / 4) * 4;
            return g == 0 ? 4 : g;
        }
        private String fit(FontMetrics fm, String text, int maxWidth) {
            if (fm.stringWidth(text) <= maxWidth) {
                return text;
            }
            String t = text;
            while (t.length() > 1 && fm.stringWidth(t + "...") > maxWidth) {
                t = t.substring(0, t.length() - 1);
            }
            return t + "...";
        }
        private void drawCentered(Graphics2D g2, String text, int cx, int y) {
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, cx - fm.stringWidth(text) / 2, y);
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean empty = data.isEmpty()
                    || (type != ChartType.DONUT && total() == 0);
            if (empty) {
                drawEmpty(g2);
            } else if (type == ChartType.DONUT) {
                drawDonut(g2);
            } else if (type == ChartType.BAR) {
                drawBar(g2);
            } else if (type == ChartType.HBAR) {
                drawHorizontalBar(g2);
            } else {
                drawLine(g2);
            }
            g2.dispose();
        }
        private void drawEmpty(Graphics2D g2) {
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            g2.setColor(mutedText);
            drawCentered(g2, "No data available", getWidth() / 2, getHeight() / 2);
        }
        private void drawDonut(Graphics2D g2) {
            int total = total();
            int diameter = Math.max(60, Math.min(150, getHeight() - 30));
            int x = 35;
            int y = (getHeight() - diameter) / 2;
            int hole = (int) (diameter * 0.5);
            if (total == 0) {
                g2.setColor(new Color(230, 235, 230));
                g2.fillOval(x, y, diameter, diameter);
            } else {
                double start = 90;
                int index = 0;
                for (Map.Entry<String, Integer> e : data.entrySet()) {
                    double extent = e.getValue() * 360.0 / total;
                    if (extent > 0) {
                        g2.setColor(colorFor(e.getKey(), index));
                        g2.fill(new Arc2D.Double(x, y, diameter, diameter,
                                start, extent, Arc2D.PIE));
                        start += extent;
                    }
                    index++;
                }
            }
            g2.setColor(Color.WHITE);
            g2.fillOval(x + (diameter - hole) / 2,
                    y + (diameter - hole) / 2, hole, hole);
            int cx = x + diameter / 2;
            int cy = y + diameter / 2;
            g2.setColor(darkGreen);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 19));
            drawCentered(g2, String.valueOf(total), cx, cy + 3);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g2.setColor(mutedText);
            drawCentered(g2, "Total", cx, cy + 19);
            int legendX = x + diameter + 50;
            int legendY = Math.max(15, y + (diameter - data.size() * 34) / 2);
            int index = 0;
            for (Map.Entry<String, Integer> e : data.entrySet()) {
                int ly = legendY + index * 34;
                g2.setColor(colorFor(e.getKey(), index));
                g2.fillRoundRect(legendX, ly, 13, 13, 4, 4);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                g2.setColor(textColor);
                g2.drawString(pretty(e.getKey()) + " Risk", legendX + 23, ly + 12);
                g2.setColor(mutedText);
                g2.drawString(String.valueOf(e.getValue()), legendX + 145, ly + 12);
                index++;
            }
        }
        private void drawBar(Graphics2D g2) {
            int left = 45, right = 20, top = 25, bottom = 35;
            int w = getWidth() - left - right;
            int h = getHeight() - top - bottom;
            int gridMax = gridMax();
            drawGrid(g2, left, top, w, h, gridMax);
            int n = data.size();
            int slot = w / n;
            int barW = Math.min(70, (int) (slot * 0.55));
            int index = 0;
            for (Map.Entry<String, Integer> e : data.entrySet()) {
                int value = e.getValue();
                int barH = (int) ((double) value / gridMax * h);
                int bx = left + index * slot + (slot - barW) / 2;
                int by = top + h - barH;
                g2.setColor(colorFor(e.getKey(), index));
                g2.fillRoundRect(bx, by, barW, Math.max(barH, 2), 6, 6);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                g2.setColor(textColor);
                drawCentered(g2, String.valueOf(value), bx + barW / 2, by - 6);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                g2.setColor(mutedText);
                drawCentered(g2,
                        fit(g2.getFontMetrics(), pretty(e.getKey()), slot - 6),
                        bx + barW / 2, top + h + 20);
                index++;
            }
        }
        private void drawHorizontalBar(Graphics2D g2) {
            int left = 120, right = 45, top = 12, bottom = 12;
            int w = getWidth() - left - right;
            int h = getHeight() - top - bottom;
            int n = data.size();
            int rowH = h / n;
            int barH = Math.max(8, Math.min(24, rowH - 10));
            int max = Math.max(1, maxValue());
            int index = 0;
            for (Map.Entry<String, Integer> e : data.entrySet()) {
                int rowY = top + index * rowH;
                int by = rowY + (rowH - barH) / 2;
                int barW = (int) ((double) e.getValue() / max * w);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                g2.setColor(textColor);
                g2.drawString(
                        fit(g2.getFontMetrics(), pretty(e.getKey()), left - 20),
                        15, by + barH / 2 + 4);
                g2.setColor(colorFor(e.getKey(), index));
                g2.fillRoundRect(left, by, Math.max(barW, 3), barH, 6, 6);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                g2.setColor(mutedText);
                g2.drawString(String.valueOf(e.getValue()),
                        left + Math.max(barW, 3) + 8, by + barH / 2 + 4);
                index++;
            }
        }
        private void drawLine(Graphics2D g2) {
            int left = 45, right = 30, top = 25, bottom = 35;
            int w = getWidth() - left - right;
            int h = getHeight() - top - bottom;
            int gridMax = gridMax();
            drawGrid(g2, left, top, w, h, gridMax);
            int n = data.size();
            int[] px = new int[n];
            int[] py = new int[n];
            String[] labels = new String[n];
            int[] values = new int[n];
            int i = 0;
            for (Map.Entry<String, Integer> e : data.entrySet()) {
                px[i] = (n == 1) ? left + w / 2 : left + i * w / (n - 1);
                py[i] = top + h - (int) ((double) e.getValue() / gridMax * h);
                labels[i] = e.getKey();
                values[i] = e.getValue();
                i++;
            }
            int[] ax = new int[n + 2];
            int[] ay = new int[n + 2];
            for (int k = 0; k < n; k++) {
                ax[k] = px[k];
                ay[k] = py[k];
            }
            ax[n] = px[n - 1];
            ay[n] = top + h;
            ax[n + 1] = px[0];
            ay[n + 1] = top + h;
            g2.setColor(new Color(53, 125, 85, 40));
            g2.fillPolygon(ax, ay, n + 2);
            g2.setColor(green);
            g2.setStroke(new BasicStroke(2.5f,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int k = 0; k < n - 1; k++) {
                g2.drawLine(px[k], py[k], px[k + 1], py[k + 1]);
            }
            g2.setStroke(new BasicStroke(1f));
            for (int k = 0; k < n; k++) {
                g2.setColor(green);
                g2.fillOval(px[k] - 5, py[k] - 5, 10, 10);
                g2.setColor(Color.WHITE);
                g2.fillOval(px[k] - 2, py[k] - 2, 4, 4);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                g2.setColor(textColor);
                drawCentered(g2, String.valueOf(values[k]), px[k], py[k] - 10);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                g2.setColor(mutedText);
                drawCentered(g2, labels[k], px[k], top + h + 20);
            }
        }
        private void drawGrid(Graphics2D g2, int left, int top,
                              int w, int h, int gridMax) {
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            for (int i = 0; i <= 4; i++) {
                int gy = top + h - (h * i / 4);
                int label = gridMax * i / 4;
                g2.setColor(new Color(232, 237, 232));
                g2.drawLine(left, gy, left + w, gy);
                g2.setColor(mutedText);
                String text = String.valueOf(label);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(text, left - 8 - fm.stringWidth(text), gy + 4);
            }
        }
    }
    // Change the role to "Admin", "Forest Officer" or "General User"
    // to preview each dashboard.
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new CommonDashboard(1, "Demo User", "Admin");
        });
    }
}
