package oracletest;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

public class RegistrationPage extends JFrame
{
    private JTextField usernameField;
    private JTextField emailField;
    private JPasswordField passwordField;
    private JPasswordField confirmField;
    private JComboBox<String> roleBox;
    private JTextField areaField;
    private JTextField cityField;
    private JTextField districtField;
    private JTextField stateField;
    private JComboBox<String> securityQuestionBox;
    private JTextField securityAnswerField;
    private JButton registerButton;

    private BufferedImage backgroundImage;

    private static final Color DARK_GREEN = new Color(10, 58, 31);
    private static final Color CREAM = new Color(250, 247, 240);
    private static final Color FIELD_BG = new Color(255, 253, 248);

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public RegistrationPage()
    {
        setTitle("Human-Animal Safety Alert & Rescue System - Register");
        setSize(1200, 850);
        setMinimumSize(new Dimension(900, 750));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        loadBackground();

        // Background panel
        JPanel background = new JPanel()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);

                if (backgroundImage != null)
                {
                    g.drawImage(backgroundImage, 0, 0,
                        getWidth(), getHeight(), this);
                }
                else
                {
                    g.setColor(new Color(20, 65, 35));
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            }
        };

        background.setLayout(new GridBagLayout());
        setContentPane(background);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // Heading
        JLabel heading = new JLabel(
            "<html><center>HUMAN-ANIMAL SAFETY ALERT<br>"
            + "&amp; RESCUE SYSTEM</center></html>"
        );
        heading.setFont(new Font("Arial", Font.BOLD, 26));
        heading.setForeground(new Color(10, 48, 28));
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);
        heading.setHorizontalAlignment(SwingConstants.CENTER);
        heading.setMaximumSize(
            new Dimension(Integer.MAX_VALUE, heading.getPreferredSize().height));

        content.add(heading);
        content.add(Box.createVerticalStrut(12));

        // Card
        JPanel card = new JPanel()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(CREAM);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);

                g2.setColor(new Color(210, 215, 205));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 25, 25);

                g2.dispose();
                super.paintComponent(g);
            }
        };

        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(18, 35, 16, 35));
        card.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("REGISTER");
        title.setFont(new Font("Arial", Font.BOLD, 26));
        title.setForeground(DARK_GREEN);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(10));

        // Form grid (two columns)
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setAlignmentX(Component.CENTER_ALIGNMENT);

        usernameField = new JTextField();
        emailField = new JTextField();
        passwordField = new JPasswordField();
        confirmField = new JPasswordField();
        roleBox = new JComboBox<>(new String[]
            { "Select Role", "Admin", "Forest Officer", "General User" });
        areaField = new JTextField();
        cityField = new JTextField();
        districtField = new JTextField();
        stateField = new JTextField();
        securityQuestionBox = new JComboBox<>(new String[]
        {
            "Select Security Question",
            "What is your pet's name?",
            "What is your mother's maiden name?",
            "What was the name of your first school?",
            "What is your favourite forest or place?",
            "What is your birth city?"
        });
        securityAnswerField = new JTextField();

        addField(form, 0, 0, 1, "Username", usernameField, "user");
        addField(form, 1, 0, 1, "Email", emailField, "mail");
        addField(form, 0, 1, 1, "Password", passwordField, "lock");
        addField(form, 1, 1, 1, "Confirm Password", confirmField, "lock");
        addField(form, 0, 2, 2, "Role", roleBox, "group");
        addField(form, 0, 3, 1, "Area", areaField, "pin");
        addField(form, 1, 3, 1, "City", cityField, "pin");
        addField(form, 0, 4, 1, "District", districtField, "pin");
        addField(form, 1, 4, 1, "State", stateField, "pin");
        addField(form, 0, 5, 1, "Security Question", securityQuestionBox, "shield");
        addField(form, 1, 5, 1, "Security Answer", securityAnswerField, "shield");

        card.add(form);
        card.add(Box.createVerticalStrut(12));

        // Buttons
        registerButton = new JButton("REGISTER   →");
        styleButton(registerButton, true);
        card.add(registerButton);
        card.add(Box.createVerticalStrut(8));

        JButton backButton = new JButton("BACK TO LOGIN");
        styleButton(backButton, false);
        card.add(backButton);

        content.add(card);
        background.add(content);

        registerButton.addActionListener(e -> registerUser());

        backButton.addActionListener(e ->
        {
            new LoginPage();
            dispose();
        });

        getRootPane().setDefaultButton(registerButton);
    }

    // Load background (supports both file names)
    private void loadBackground()
    {
        String[] names =
            { "/oracletest/forest_background.png",
              "/oracletest/forest_background.png.png" };

        for (String name : names)
        {
            try
            {
                java.net.URL url = RegistrationPage.class.getResource(name);

                if (url != null)
                {
                    backgroundImage = ImageIO.read(url);
                    return;
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        System.err.println("Background image not found - using plain colour.");
    }

    // Adds a label + icon field to the grid
    private void addField(JPanel form, int col, int row, int span,
                          String labelText, JComponent field, String icon)
    {
        JPanel cell = new JPanel();
        cell.setOpaque(false);
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));

        JLabel label = new JLabel(labelText);
        label.setFont(new Font("Arial", Font.BOLD, 13));
        label.setForeground(new Color(20, 40, 30));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 3, 0));

        JPanel fieldPanel = createFieldPanel(field, icon);

        cell.add(label);
        cell.add(fieldPanel);

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = col;
        gc.gridy = row;
        gc.gridwidth = span;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(0, col == 0 ? 0 : 8, 8, col == 0 && span == 1 ? 8 : 0);

        form.add(cell, gc);
    }

    private JPanel createFieldPanel(JComponent field, String type)
    {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setBackground(FIELD_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(145, 160, 155), 1),
            new EmptyBorder(2, 10, 2, 8)));

        JLabel iconLabel = new JLabel(new FormIcon(type));
        panel.add(iconLabel, BorderLayout.WEST);

        field.setFont(new Font("Arial", Font.PLAIN, 14));
        field.setBackground(FIELD_BG);
        if (!(field instanceof JComboBox))
        {
            field.setBorder(new EmptyBorder(4, 4, 4, 4));
        }
        panel.add(field, BorderLayout.CENTER);

        panel.setPreferredSize(new Dimension(330, 38));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    // Custom icons
    static class FormIcon implements Icon
    {
        private final String type;

        FormIcon(String type)
        {
            this.type = type;
        }

        public int getIconWidth() { return 22; }

        public int getIconHeight() { return 22; }

        public void paintIcon(Component c, Graphics g, int x, int y)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(10, 45, 35));
            g2.setStroke(new BasicStroke(2));

            switch (type)
            {
                case "user":
                    g2.fillOval(x + 7, y + 2, 9, 9);
                    g2.fillRoundRect(x + 3, y + 12, 17, 9, 7, 7);
                    break;

                case "lock":
                    g2.drawArc(x + 6, y + 1, 11, 13, 0, 180);
                    g2.fillRoundRect(x + 3, y + 9, 17, 12, 3, 3);
                    g2.setColor(Color.WHITE);
                    g2.fillOval(x + 10, y + 13, 3, 3);
                    break;

                case "group":
                    g2.fillOval(x + 7, y + 2, 8, 8);
                    g2.fillOval(x + 1, y + 6, 6, 6);
                    g2.fillOval(x + 15, y + 6, 6, 6);
                    g2.fillRoundRect(x + 5, y + 12, 14, 9, 5, 5);
                    g2.fillRoundRect(x, y + 14, 7, 7, 4, 4);
                    g2.fillRoundRect(x + 16, y + 14, 6, 7, 4, 4);
                    break;

                case "mail":
                    g2.setStroke(new BasicStroke(1.8f));
                    g2.drawRoundRect(x + 1, y + 5, 19, 13, 3, 3);
                    g2.drawLine(x + 2, y + 6, x + 11, y + 13);
                    g2.drawLine(x + 20, y + 6, x + 11, y + 13);
                    break;

                case "pin":
                    g2.fillOval(x + 5, y + 2, 12, 12);
                    g2.fillPolygon(new int[]{x + 6, x + 16, x + 11},
                                   new int[]{y + 11, y + 11, y + 21}, 3);
                    g2.setColor(Color.WHITE);
                    g2.fillOval(x + 9, y + 6, 4, 4);
                    break;

                case "shield":
                    g2.fillPolygon(new int[]{x + 3, x + 19, x + 19, x + 11, x + 3},
                                   new int[]{y + 3, y + 3, y + 12, y + 21, y + 12}, 5);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2));
                    g2.drawLine(x + 7, y + 11, x + 10, y + 14);
                    g2.drawLine(x + 10, y + 14, x + 15, y + 8);
                    break;

                default:
                    break;
            }

            g2.dispose();
        }
    }

    private void styleButton(JButton button, boolean primary)
    {
        button.setFont(new Font("Arial", Font.BOLD, 15));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(330, 42));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setOpaque(true);

        if (primary)
        {
            button.setBackground(new Color(5, 65, 43));
            button.setForeground(Color.WHITE);
            button.setBorder(BorderFactory.createLineBorder(new Color(5, 65, 43), 1));
        }
        else
        {
            button.setBackground(CREAM);
            button.setForeground(DARK_GREEN);
            button.setBorder(BorderFactory.createLineBorder(new Color(155, 165, 155), 1));
        }
    }

    private void showMessage(String msg)
    {
        JOptionPane.showMessageDialog(this, msg);
    }

    // Validate form and save to Oracle
    private void registerUser()
    {
        final String username = usernameField.getText().trim();
        final String email = emailField.getText().trim();
        final String password = new String(passwordField.getPassword());
        final String confirm = new String(confirmField.getPassword());
        final String role = (String) roleBox.getSelectedItem();
        final String area = areaField.getText().trim();
        final String city = cityField.getText().trim();
        final String district = districtField.getText().trim();
        final String state = stateField.getText().trim();
        final String question = (String) securityQuestionBox.getSelectedItem();
        final String answer = securityAnswerField.getText().trim().toLowerCase();

        if (username.isEmpty() || email.isEmpty() || password.isEmpty()
            || confirm.isEmpty() || area.isEmpty() || city.isEmpty()
            || district.isEmpty() || state.isEmpty() || answer.isEmpty())
        {
            showMessage("Please fill in all the fields.");
            return;
        }

        if (username.length() < 3)
        {
            showMessage("Username must be at least 3 characters.");
            return;
        }

        if (!EMAIL_PATTERN.matcher(email).matches())
        {
            showMessage("Please enter a valid email address.");
            return;
        }

        if (password.length() < 6)
        {
            showMessage("Password must be at least 6 characters.");
            return;
        }

        if (!password.equals(confirm))
        {
            showMessage("Password and Confirm Password do not match.");
            return;
        }

        if ("Select Role".equals(role))
        {
            showMessage("Please select your role.");
            return;
        }

        if (securityQuestionBox.getSelectedIndex() == 0)
        {
            showMessage("Please select a security question.");
            return;
        }

        registerButton.setEnabled(false);
        registerButton.setText("Saving...");

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>()
        {
            // Returns null on success, otherwise an error message
            @Override
            protected String doInBackground() throws Exception
            {
                String checkSql =
                    "SELECT COUNT(*) FROM USER_ACCOUNT "
                    + "WHERE LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?)";

                String insertSql =
                    "INSERT INTO USER_ACCOUNT "
                    + "(username, password, email, role, area, city, "
                    + "district, state, security_question, security_answer) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                try (Connection con = DBConnection.getConnection())
                {
                    try (PreparedStatement ps = con.prepareStatement(checkSql))
                    {
                        ps.setString(1, username);
                        ps.setString(2, email);

                        try (ResultSet rs = ps.executeQuery())
                        {
                            if (rs.next() && rs.getInt(1) > 0)
                            {
                                return "Username or email is already registered.";
                            }
                        }
                    }

                    try (PreparedStatement ps = con.prepareStatement(insertSql))
                    {
                        ps.setString(1, username);
                        ps.setString(2, password);
                        ps.setString(3, email);
                        ps.setString(4, role);
                        ps.setString(5, area);
                        ps.setString(6, city);
                        ps.setString(7, district);
                        ps.setString(8, state);
                        ps.setString(9, question);
                        ps.setString(10, answer);
                        ps.executeUpdate();
                    }

                    if (!con.getAutoCommit())
                    {
                        con.commit();
                    }
                }
                catch (SQLException ex)
                {
                    // ORA-00001 = unique constraint violated
                    if (ex.getErrorCode() == 1)
                    {
                        return "Username or email is already registered.";
                    }
                    throw ex;
                }

                return null;
            }

            @Override
            protected void done()
            {
                try
                {
                    String error = get();

                    if (error == null)
                    {
                        showMessage("Registration successful!\nPlease login now.");
                        new LoginPage();
                        dispose();
                        return;
                    }

                    showMessage(error);
                }
                catch (Exception ex)
                {
                    Throwable cause = ex.getCause();
                    showMessage("Registration failed:\n"
                        + (cause != null ? cause.getMessage() : ex.getMessage()));
                }

                registerButton.setEnabled(true);
                registerButton.setText("REGISTER   →");
            }
        };

        worker.execute();
    }

    public static void main(String[] args)
    {
        SwingUtilities.invokeLater(() -> new RegistrationPage().setVisible(true));
    }
}