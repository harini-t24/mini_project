package oracletest;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.*;
import java.util.Date;

public class AddAnimalSighting extends JFrame {

    // Must match the DESCRIPTION column size (see wildlife_setup.sql: 200)
    private static final int DESCRIPTION_MAX = 200;
    private static final int RESCUE_DETAILS_MAX = 100;

    private final int userId;
    private final String username;

    // Loaded from the reporter's account
    private String reporterRole = "";
    private String defaultCity = "";
    private String defaultDistrict = "";

    // Animal selection
    private JComboBox<String> animalTypeCombo;
    private JComboBox<String> habitatCombo;
    private JComboBox<String> animalCombo;
    private JTextField otherAnimalField;

    // Sighting details
    private JTextField areaField;
    private JTextField cityField;
    private JTextField districtField;
    private JTextField distanceField;
    private JTextField conflictTypeField;
    private JTextArea descriptionArea;
    private JSpinner sightingDateSpinner;
    private JSpinner conflictDateSpinner;
    private JCheckBox hasConflictCheck;
    private JCheckBox addConflictDateCheck;

    // Rare species
    private JCheckBox rareCheck;

    // Rescue details
    private JCheckBox rescueCheck;
    private JComboBox<String> conditionCombo;
    private JSpinner animalCountSpinner;
    private JTextField contactPhoneField;
    private JTextArea rescueDetailsArea;

    private final Color background = new Color(246, 248, 245);
    private final Color white = Color.WHITE;
    private final Color darkGreen = new Color(24, 75, 53);
    private final Color green = new Color(53, 125, 85);
    private final Color textColor = new Color(45, 55, 48);
    private final Color borderColor = new Color(220, 230, 220);

    public AddAnimalSighting(int userId, String username) {
        this.userId = userId;
        this.username = username;

        setTitle("Report Animal Sighting");
        setSize(780, 880);
        setMinimumSize(new Dimension(650, 680));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        loadReporterProfile();
        createInterface();
        setVisible(true);
    }

    // role, city and district of the person reporting (city / district are
    // pre-filled in the form and can be corrected)
    private void loadReporterProfile() {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT ROLE, CITY, DISTRICT FROM USER_ACCOUNT WHERE USER_ID = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    reporterRole = rs.getString(1) == null ? "" : rs.getString(1);
                    defaultCity = rs.getString(2) == null ? "" : rs.getString(2);
                    defaultDistrict = rs.getString(3) == null ? "" : rs.getString(3);
                }
            }
        } catch (SQLException ex) {
            // the form still works; city / district are simply left empty
        }
    }

    // officers and admins may see rare species names, the public may not
    private boolean canSeeRare() {
        return AreaRiskService.isManagerRole(reporterRole);
    }

    // -------------------------------------------------
    // MAIN INTERFACE
    // -------------------------------------------------

    private void createInterface() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(background);
        mainPanel.setBorder(new EmptyBorder(25, 35, 25, 35));

        JPanel headingPanel = new JPanel();
        headingPanel.setBackground(background);
        headingPanel.setLayout(new BoxLayout(headingPanel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Report Animal Sighting");
        title.setFont(new Font("Segoe UI", Font.BOLD, 27));
        title.setForeground(darkGreen);

        JLabel subtitle = new JLabel(
                "Enter the details of the animal you observed.");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(Color.GRAY);

        headingPanel.add(title);
        headingPanel.add(Box.createVerticalStrut(6));
        headingPanel.add(subtitle);
        mainPanel.add(headingPanel, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(white);
        formPanel.setBorder(new EmptyBorder(20, 25, 20, 25));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(7, 8, 7, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        // ----- Components
        animalTypeCombo = new JComboBox<>(
                new String[]{"Select Type", "Wild", "Domestic"});
        habitatCombo = new JComboBox<>(new String[]{"Select Habitat"});
        animalCombo = new JComboBox<>(new String[]{"Select Animal"});
        otherAnimalField = new JTextField();
        otherAnimalField.setEnabled(false);

        areaField = new JTextField();
        cityField = new JTextField(defaultCity);
        districtField = new JTextField(defaultDistrict);
        distanceField = new JTextField();
        conflictTypeField = new JTextField();
        sightingDateSpinner = createDateSpinner();
        conflictDateSpinner = createDateSpinner();

        hasConflictCheck = new JCheckBox("Yes, a conflict occurred");
        addConflictDateCheck = new JCheckBox("Enter conflict date");
        hasConflictCheck.setBackground(white);
        addConflictDateCheck.setBackground(white);

        rareCheck = new JCheckBox("Yes, this is a rare / endangered species");
        rareCheck.setBackground(white);

        descriptionArea = new JTextArea(3, 20);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        // Rescue components
        rescueCheck = new JCheckBox(
                "Yes, the animal needs rescue (stray / injured)");
        rescueCheck.setBackground(white);

        conditionCombo = new JComboBox<>(new String[]{
                "Select Condition", "Stray", "Injured", "Sick",
                "Trapped", "Abandoned young"});
        animalCountSpinner = new JSpinner(
                new SpinnerNumberModel(1, 1, 999, 1));
        contactPhoneField = new JTextField();
        rescueDetailsArea = new JTextArea(3, 20);
        rescueDetailsArea.setLineWrap(true);
        rescueDetailsArea.setWrapStyleWord(true);
        rescueDetailsArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        setRescueFieldsEnabled(false);

        // ----- Events
        animalTypeCombo.addActionListener(e -> updateHabitatOptions());
        habitatCombo.addActionListener(e -> updateAnimalOptions());
        animalCombo.addActionListener(e -> updateOtherAnimalField());

        hasConflictCheck.addActionListener(e -> {
            boolean selected = hasConflictCheck.isSelected();
            conflictTypeField.setEnabled(selected);
            addConflictDateCheck.setEnabled(selected);
            conflictDateSpinner.setEnabled(
                    selected && addConflictDateCheck.isSelected());
        });

        addConflictDateCheck.addActionListener(e ->
                conflictDateSpinner.setEnabled(
                        hasConflictCheck.isSelected()
                        && addConflictDateCheck.isSelected()));

        rescueCheck.addActionListener(e ->
                setRescueFieldsEnabled(rescueCheck.isSelected()));

        conflictTypeField.setEnabled(false);
        addConflictDateCheck.setEnabled(false);
        conflictDateSpinner.setEnabled(false);

        // ----- Rows
        int row = 0;
        addSection(formPanel, gbc, row++, "ANIMAL DETAILS");
        addFormRow(formPanel, gbc, row++, "Animal Type *", animalTypeCombo);
        addFormRow(formPanel, gbc, row++, "Habitat Category *", habitatCombo);
        addFormRow(formPanel, gbc, row++, "Select Animal *", animalCombo);
        addFormRow(formPanel, gbc, row++, "Enter Other Animal", otherAnimalField);
        addFormRow(formPanel, gbc, row++, "Rare Species?", rareCheck);

        addSection(formPanel, gbc, row++, "SIGHTING DETAILS");
        addFormRow(formPanel, gbc, row++, "Area Name *", areaField);
        addFormRow(formPanel, gbc, row++, "City *", cityField);
        addFormRow(formPanel, gbc, row++, "District *", districtField);
        addFormRow(formPanel, gbc, row++, "Sighting Date *", sightingDateSpinner);
        addFormRow(formPanel, gbc, row++, "Distance (km)", distanceField);
        addFormRow(formPanel, gbc, row++, "Has Conflict?", hasConflictCheck);
        addFormRow(formPanel, gbc, row++, "Conflict Type", conflictTypeField);
        addFormRow(formPanel, gbc, row++, "Conflict Date", createConflictDatePanel());
        addFormRow(formPanel, gbc, row++, "Description",
                new JScrollPane(descriptionArea));

        addSection(formPanel, gbc, row++, "RESCUE DETAILS (stray / injured animals)");
        addFormRow(formPanel, gbc, row++, "Needs Rescue?", rescueCheck);
        addFormRow(formPanel, gbc, row++, "Animal Condition *", conditionCombo);
        addFormRow(formPanel, gbc, row++, "Number of Animals", animalCountSpinner);
        addFormRow(formPanel, gbc, row++, "Contact Phone *", contactPhoneField);
        addFormRow(formPanel, gbc, row++, "Landmark / Details",
                new JScrollPane(rescueDetailsArea));

        // ----- Buttons
        JButton historyButton = new JButton("Check Area History");
        JButton submitButton = new JButton("Submit Report");
        JButton cancelButton = new JButton("Cancel");

        styleButton(historyButton, Color.WHITE, darkGreen);
        historyButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(green),
                new EmptyBorder(10, 19, 10, 19)));
        styleButton(submitButton, green, Color.WHITE);
        styleButton(cancelButton, new Color(235, 238, 235), darkGreen);

        historyButton.addActionListener(e -> checkAreaHistory());
        submitButton.addActionListener(e -> saveSighting());
        cancelButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        buttonPanel.setBackground(background);
        buttonPanel.add(historyButton);
        buttonPanel.add(cancelButton);
        buttonPanel.add(submitButton);

        JScrollPane formScroll = new JScrollPane(formPanel);
        formScroll.setBorder(null);
        formScroll.getVerticalScrollBar().setUnitIncrement(15);

        mainPanel.add(formScroll, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        add(mainPanel);
    }

    // -------------------------------------------------
    // RESCUE FIELDS
    // -------------------------------------------------

    private void setRescueFieldsEnabled(boolean enabled) {
        conditionCombo.setEnabled(enabled);
        animalCountSpinner.setEnabled(enabled);
        contactPhoneField.setEnabled(enabled);
        rescueDetailsArea.setEnabled(enabled);
        if (!enabled) {
            conditionCombo.setSelectedIndex(0);
            animalCountSpinner.setValue(1);
            contactPhoneField.setText("");
            rescueDetailsArea.setText("");
        }
    }

    // -------------------------------------------------
    // AREA HISTORY PREVIEW (before submitting)
    // -------------------------------------------------

    private void checkAreaHistory() {
        String area = areaField.getText().trim();
        if (area.isEmpty()) {
            showMessage("Please enter the area name first.");
            return;
        }
        try {
            AreaRiskService.AreaHistory history =
                    AreaRiskService.loadHistory(area, !canSeeRare());
            AreaRiskService.showAreaCheck(this, history, false, null);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Unable to load area history:\n" + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // -------------------------------------------------
    // ANIMAL LISTS
    // -------------------------------------------------

    private String[] getAnimalList(String type, String habitat) {
        if (type.equals("Wild")) {
            switch (habitat) {
                case "Land":
                    return new String[]{"Elephant", "Leopard", "Tiger", "Bear",
                        "Lion", "Wild Pig", "Monkey", "Nilgiri Thar",
                        "Spotted Deer", "Indian Fox", "Indian Porcupine",
                        "Tortoise", "Snake", "Others"};
                case "Water":
                    return new String[]{"Crocodile", "Smooth-coated Otter",
                        "Asian Small-clawed Otter", "Fishing Cat",
                        "Bengal Monitor", "Turtle", "Others"};
                case "Both":
                    return new String[]{"Asian Elephant", "Wild Boar", "Tiger",
                        "Leopard", "Smooth-coated Otter", "Fishing Cat",
                        "Crocodile", "Indian Rock Python", "Turtle", "Others"};
            }
        }
        if (type.equals("Domestic")) {
            switch (habitat) {
                case "Land":
                    return new String[]{"Cow", "Goat", "Dog", "Cat", "Sheep",
                        "Horse", "Donkey", "Chicken", "Camel", "Rabbit",
                        "Others"};
                case "Water":
                    return new String[]{"Duck", "Goose", "Fishes", "Others"};
                case "Both":
                    return new String[]{"Water Buffalo", "Domestic Pig",
                        "Duck", "Goose", "Others"};
            }
        }
        return new String[]{"Select Animal"};
    }

    private void updateHabitatOptions() {
        String type = (String) animalTypeCombo.getSelectedItem();
        habitatCombo.removeAllItems();
        animalCombo.removeAllItems();
        otherAnimalField.setText("");
        otherAnimalField.setEnabled(false);
        animalCombo.addItem("Select Animal");

        if (type == null || type.equals("Select Type")) {
            habitatCombo.addItem("Select Habitat");
            habitatCombo.setEnabled(false);
            animalCombo.setEnabled(false);
            return;
        }
        habitatCombo.setEnabled(true);
        animalCombo.setEnabled(true);
        habitatCombo.addItem("Select Habitat");
        habitatCombo.addItem("Land");
        habitatCombo.addItem("Water");
        habitatCombo.addItem("Both");
    }

    private void updateAnimalOptions() {
        String type = (String) animalTypeCombo.getSelectedItem();
        String habitat = (String) habitatCombo.getSelectedItem();
        animalCombo.removeAllItems();
        otherAnimalField.setText("");
        otherAnimalField.setEnabled(false);

        if (type == null || habitat == null
                || type.equals("Select Type")
                || habitat.equals("Select Habitat")) {
            animalCombo.addItem("Select Animal");
            return;
        }
        for (String animal : getAnimalList(type, habitat)) {
            animalCombo.addItem(animal);
        }
    }

    private void updateOtherAnimalField() {
        boolean isOther = "Others".equals(animalCombo.getSelectedItem());
        otherAnimalField.setEnabled(isOther);
        if (!isOther) {
            otherAnimalField.setText("");
        }
    }

    // -------------------------------------------------
    // UI HELPERS
    // -------------------------------------------------

    private JPanel createConflictDatePanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 0, 5));
        panel.setBackground(white);
        panel.add(addConflictDateCheck);
        panel.add(conflictDateSpinner);
        return panel;
    }

    private JSpinner createDateSpinner() {
        JSpinner spinner = new JSpinner(new SpinnerDateModel(
                new Date(), null, null, java.util.Calendar.DAY_OF_MONTH));
        spinner.setEditor(new JSpinner.DateEditor(spinner, "dd-MM-yyyy"));
        return spinner;
    }

    private void addSection(JPanel panel, GridBagConstraints gbc,
                            int row, String text) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setForeground(green);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor),
                new EmptyBorder(10, 0, 4, 0)));
        panel.add(label, gbc);
        gbc.gridwidth = 1;
    }

    private void addFormRow(JPanel panel, GridBagConstraints gbc,
                            int row, String labelText, Component component) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.35;

        JLabel label = new JLabel(labelText);
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        label.setForeground(textColor);
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.65;

        if (component instanceof JTextField
                || component instanceof JComboBox
                || component instanceof JSpinner) {
            component.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            component.setPreferredSize(new Dimension(250, 32));
        }
        panel.add(component, gbc);
    }

    private void styleButton(JButton button, Color bg, Color fg) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setBackground(bg);
        button.setForeground(fg);
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(11, 20, 11, 20));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    // -------------------------------------------------
    // SAVE SIGHTING TO ORACLE, THEN SHOW THE RISK POPUP
    // -------------------------------------------------

    private void saveSighting() {
        String type = (String) animalTypeCombo.getSelectedItem();
        String habitat = (String) habitatCombo.getSelectedItem();
        String selectedAnimal = (String) animalCombo.getSelectedItem();

        String species = "Others".equals(selectedAnimal)
                ? otherAnimalField.getText().trim() : selectedAnimal;

        String area = AreaRiskService.tidy(areaField.getText());
        String city = AreaRiskService.tidy(cityField.getText());
        String district = AreaRiskService.tidy(districtField.getText());
        String distanceText = distanceField.getText().trim();
        String conflictType = conflictTypeField.getText().trim();
        String description = descriptionArea.getText().trim();
        boolean hasConflict = hasConflictCheck.isSelected();
        boolean isRare = rareCheck.isSelected();
        boolean needsRescue = rescueCheck.isSelected();

        // ----- Validation
        if (type == null || type.equals("Select Type")
                || habitat == null || habitat.equals("Select Habitat")
                || selectedAnimal == null
                || selectedAnimal.equals("Select Animal")) {
            showMessage("Please select animal type, habitat and animal.");
            return;
        }
        if (species == null || species.trim().isEmpty()) {
            showMessage("Please enter the animal name.");
            return;
        }
        if (species.length() > 25) {
            showMessage("Animal name cannot exceed 25 characters.");
            return;
        }
        if (area.isEmpty()) {
            showMessage("Please enter the area name.");
            return;
        }
        if (area.length() > 25) {
            showMessage("Area name cannot exceed 25 characters.");
            return;
        }
        if (city.isEmpty() || district.isEmpty()) {
            showMessage("Please enter the city and the district so the alert "
                    + "reaches the right people.");
            return;
        }
        if (city.length() > 25 || district.length() > 25) {
            showMessage("City and district cannot exceed 25 characters.");
            return;
        }
        if (hasConflict && conflictType.isEmpty()) {
            showMessage("Please enter the conflict type.");
            return;
        }
        if (conflictType.length() > 25) {
            showMessage("Conflict type cannot exceed 25 characters.");
            return;
        }
        if (description.length() > DESCRIPTION_MAX) {
            showMessage("Description cannot exceed " + DESCRIPTION_MAX
                    + " characters.");
            return;
        }

        double distance = 0;
        if (!distanceText.isEmpty()) {
            try {
                distance = Double.parseDouble(distanceText);
                if (distance < 0 || distance > 999.99) {
                    showMessage("Distance must be between 0 and 999.99.");
                    return;
                }
            } catch (NumberFormatException ex) {
                showMessage("Please enter a valid numeric distance.");
                return;
            }
        }

        // Rescue validation
        String condition = null;
        int animalCount = 0;
        String phone = contactPhoneField.getText().trim();
        String rescueDetails = rescueDetailsArea.getText().trim();
        if (needsRescue) {
            condition = (String) conditionCombo.getSelectedItem();
            if (condition == null || condition.equals("Select Condition")) {
                showMessage("Please select the animal condition for the rescue.");
                return;
            }
            if (phone.isEmpty()) {
                showMessage("Please enter a contact phone number for the rescue team.");
                return;
            }
            if (!phone.matches("^[+0-9 \\-]{7,15}$")) {
                showMessage("Please enter a valid phone number.");
                return;
            }
            if (rescueDetails.length() > RESCUE_DETAILS_MAX) {
                showMessage("Landmark / details cannot exceed "
                        + RESCUE_DETAILS_MAX + " characters.");
                return;
            }
            animalCount = (Integer) animalCountSpinner.getValue();
        }

        Date sightingDate = (Date) sightingDateSpinner.getValue();
        Date conflictDate = null;
        if (hasConflict && addConflictDateCheck.isSelected()) {
            conflictDate = (Date) conflictDateSpinner.getValue();
        }

        // ----- Area history BEFORE saving (so the new report is not counted)
        AreaRiskService.AreaHistory history = null;
        try {
            history = AreaRiskService.loadHistory(area, !canSeeRare());
        } catch (SQLException ex) {
            // the report can still be saved; the popup just has no history
        }

        String sql =
                "INSERT INTO ANIMAL_SIGHTING "
                + "(ANIMAL_CATEGORY, HABITAT_CATEGORY, SPECIES_NAME, "
                + "AREA_NAME, SIGHTING_DATE, DISTANCE, "
                + "VERIFICATION_STATUS, HAS_CONFLICT, "
                + "CONFLICT_TYPE, CONFLICT_DATE, DESCRIPTION, "
                + "REPORTED_BY, IS_RARE, NEEDS_RESCUE, ANIMAL_CONDITION, "
                + "ANIMAL_COUNT, CONTACT_PHONE, RESCUE_DETAILS, CITY, DISTRICT) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int newSightingId = 0;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, type);
            ps.setString(2, habitat.toUpperCase());
            ps.setString(3, species);
            ps.setString(4, area);
            ps.setDate(5, new java.sql.Date(sightingDate.getTime()));

            if (distanceText.isEmpty()) {
                ps.setNull(6, Types.NUMERIC);
            } else {
                ps.setDouble(6, distance);
            }

            ps.setString(7, "PENDING");
            ps.setInt(8, hasConflict ? 1 : 0);

            if (hasConflict) {
                ps.setString(9, conflictType);
            } else {
                ps.setNull(9, Types.VARCHAR);
            }

            if (conflictDate != null) {
                ps.setDate(10, new java.sql.Date(conflictDate.getTime()));
            } else {
                ps.setNull(10, Types.DATE);
            }

            if (description.isEmpty()) {
                ps.setNull(11, Types.VARCHAR);
            } else {
                ps.setString(11, description);
            }

            ps.setInt(12, userId);
            ps.setInt(13, isRare ? 1 : 0);
            ps.setInt(14, needsRescue ? 1 : 0);

            if (needsRescue) {
                ps.setString(15, condition);
                ps.setInt(16, animalCount);
                ps.setString(17, phone);
                if (rescueDetails.isEmpty()) {
                    ps.setNull(18, Types.VARCHAR);
                } else {
                    ps.setString(18, rescueDetails);
                }
            } else {
                ps.setNull(15, Types.VARCHAR);
                ps.setNull(16, Types.NUMERIC);
                ps.setNull(17, Types.VARCHAR);
                ps.setNull(18, Types.VARCHAR);
            }
            ps.setString(19, city);
            ps.setString(20, district);

            int result = ps.executeUpdate();
            if (result <= 0) {
                return;
            }

            // Read back the id of the row just saved (needed for RISK_RECORD)
            newSightingId = findNewSightingId(con, species, area);

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Unable to save animal sighting:\n" + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JOptionPane.showMessageDialog(this,
                "Animal sighting reported successfully!\n"
                + "Your report is pending verification.",
                "Success", JOptionPane.INFORMATION_MESSAGE);

        // Routing: who is told (officer only / public alert), risk record,
        // rescue case and the warning popup all happen here
        AreaRiskService.SightingInfo info = new AreaRiskService.SightingInfo();
        info.sightingId = newSightingId;
        info.reporterId = userId;
        info.reporterRole = reporterRole;
        info.category = type;
        info.species = species;
        info.area = area;
        info.city = city;
        info.district = district;
        info.distance = distanceText.isEmpty() ? Double.NaN : distance;
        info.conflict = hasConflict;
        info.rare = isRare;
        info.needsRescue = needsRescue;
        info.condition = condition;
        info.phone = needsRescue ? phone : null;
        info.details = needsRescue ? rescueDetails : null;
        AreaRiskService.handleNewSighting(this, history, info);

        dispose();
    }

    private int findNewSightingId(Connection con, String species, String area)
            throws SQLException {
        String sql = "SELECT MAX(SIGHTING_ID) FROM ANIMAL_SIGHTING "
                + "WHERE REPORTED_BY = ? AND SPECIES_NAME = ? AND AREA_NAME = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, species);
            ps.setString(3, area);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message, "Validation",
                JOptionPane.WARNING_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AddAnimalSighting(1, "Test User"));
    }
}