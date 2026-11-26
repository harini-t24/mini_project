package oracletest;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class LoginPage extends JFrame
{
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;

    private BufferedImage backgroundImage;

    private static final Color DARK_GREEN = new Color(10, 58, 31);
    private static final Color CREAM = new Color(250, 247, 240);
    private static final Color FIELD_BG = new Color(255, 253, 248);

    public LoginPage()
    {
        setTitle("Human-Animal Safety Alert & Rescue System");
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
        heading.setFont(new Font("Arial", Font.BOLD, 29));
        heading.setForeground(new Color(10, 48, 28));
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);
        heading.setHorizontalAlignment(SwingConstants.CENTER);
        heading.setMaximumSize(
            new Dimension(Integer.MAX_VALUE, heading.getPreferredSize().height));

        JLabel subtitle = new JLabel(
            "Monitor  •  Predict  •  Protect  •  Rescue");
        subtitle.setFont(new Font("Arial", Font.BOLD, 17));
        subtitle.setForeground(new Color(10, 48, 28));
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        content.add(heading);
        content.add(Box.createVerticalStrut(5));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(20));

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
        card.setBorder(new EmptyBorder(25, 35, 25, 35));
        // Height increased (420 -> 465) to make room for the Forgot Password link
        card.setPreferredSize(new Dimension(440, 465));
        card.setMinimumSize(new Dimension(440, 465));
        card.setMaximumSize(new Dimension(440, 465));
        card.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Title
        JLabel loginTitle = new JLabel("LOGIN");
        loginTitle.setFont(new Font("Arial", Font.BOLD, 29));
        loginTitle.setForeground(DARK_GREEN);
        loginTitle.setHorizontalAlignment(SwingConstants.CENTER);
        loginTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginTitle.setPreferredSize(new Dimension(370, 45));
        loginTitle.setMaximumSize(new Dimension(370, 45));
        card.add(loginTitle);
        card.add(Box.createVerticalStrut(22));

        // Username
        card.add(createFormLabel("Username"));
        usernameField = new JTextField();
        usernameField.setToolTipText("Enter your username");
        card.add(createFieldPanel(usernameField, "user"));
        card.add(Box.createVerticalStrut(16));

        // Password
        card.add(createFormLabel("Password"));
        passwordField = new JPasswordField();
        passwordField.setToolTipText("Enter your password");
        card.add(createFieldPanel(passwordField, "lock"));

        // Forgot password link (right aligned, under the password field)
        card.add(Box.createVerticalStrut(8));
        card.add(createForgotPasswordLink());
        card.add(Box.createVerticalStrut(18));

        // Login button
        loginButton = new JButton("LOGIN   →");
        styleButton(loginButton, true);
        card.add(loginButton);
        card.add(Box.createVerticalStrut(12));

        // Register button
        JButton registerButton = new JButton("REGISTER HERE");
        styleButton(registerButton, false);
        card.add(registerButton);

        content.add(card);
        background.add(content);

        // Actions
        loginButton.addActionListener(e -> loginUser());

        registerButton.addActionListener(e ->
        {
            new RegistrationPage().setVisible(true);
            dispose();
        });

        // Enter key triggers login
        getRootPane().setDefaultButton(loginButton);

        setVisible(true);
    }

    // "Forgot password?" link that opens the ForgotPassword page
    private JPanel createForgotPasswordLink()
    {
        JLabel link = new JLabel("Forgot password?");
        link.setFont(new Font("Arial", Font.BOLD, 13));
        link.setForeground(new Color(5, 65, 43));
        link.setCursor(new Cursor(Cursor.HAND_CURSOR));
        link.setToolTipText("Reset your password");

        link.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                openForgotPassword();
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                link.setText("<html><u>Forgot password?</u></html>");
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                link.setText("Forgot password?");
            }
        });

        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        row.setOpaque(false);
        row.add(link);
        row.setPreferredSize(new Dimension(360, 20));
        row.setMaximumSize(new Dimension(360, 20));
        row.setAlignmentX(Component.CENTER_ALIGNMENT);
        return row;
    }

    // Navigate to the Forgot Password page
    private void openForgotPassword()
    {
        new ForgotPassword();   // ForgotPassword shows itself in its constructor
        dispose();
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
                java.net.URL url = LoginPage.class.getResource(name);

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

    private JLabel createFormLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Arial", Font.BOLD, 14));
        label.setForeground(new Color(20, 40, 30));
        label.setPreferredSize(new Dimension(360, 23));
        label.setMaximumSize(new Dimension(360, 23));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }

    private JPanel createFieldPanel(JComponent field, String type)
    {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setBackground(FIELD_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(145, 160, 155), 1),
            new EmptyBorder(3, 10, 3, 10)));

        // Icons are shared with the registration page
        panel.add(new JLabel(new RegistrationPage.FormIcon(type)),
            BorderLayout.WEST);

        field.setFont(new Font("Arial", Font.PLAIN, 15));
        field.setBorder(new EmptyBorder(5, 5, 5, 5));
        field.setBackground(FIELD_BG);
        panel.add(field, BorderLayout.CENTER);

        panel.setPreferredSize(new Dimension(360, 45));
        panel.setMaximumSize(new Dimension(360, 45));
        panel.setAlignmentX(Component.CENTER_ALIGNMENT);
        return panel;
    }

    private void styleButton(JButton button, boolean primary)
    {
        button.setFont(new Font("Arial", Font.BOLD, 15));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(360, 45));
        button.setMaximumSize(new Dimension(360, 45));
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

    // Database authentication (role is read from the database)
    private void loginUser()
    {
        final String username = usernameField.getText().trim();
        final String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty())
        {
            JOptionPane.showMessageDialog(this,
                "Please enter username and password.");
            return;
        }

        loginButton.setEnabled(false);
        loginButton.setText("Checking...");

        SwingWorker<String[], Void> worker = new SwingWorker<String[], Void>()
        {
            @Override
            protected String[] doInBackground() throws Exception
            {
                String sql =
                    "SELECT user_id, username, role FROM USER_ACCOUNT "
                    + "WHERE username = ? AND password = ?";

                try (Connection con = DBConnection.getConnection();
                     PreparedStatement ps = con.prepareStatement(sql))
                {
                    ps.setString(1, username);
                    ps.setString(2, password);

                    try (ResultSet rs = ps.executeQuery())
                    {
                        if (rs.next())
                        {
                            return new String[]
                            {
                                rs.getString("user_id"),
                                rs.getString("username"),
                                rs.getString("role")
                            };
                        }
                        return null;
                    }
                }
            }

            @Override
            protected void done()
            {
                try
                {
                    String[] user = get();

                    if (user != null)
                    {
                        JOptionPane.showMessageDialog(LoginPage.this,
                            "Login Successful!\nWelcome, " + user[1]);

                        new CommonDashboard(
                            Integer.parseInt(user[0]), user[1], user[2]);

                        dispose();
                    }
                    else
                    {
                        JOptionPane.showMessageDialog(LoginPage.this,
                            "Invalid username or password.");
                    }
                }
                catch (Exception ex)
                {
                    Throwable cause = ex.getCause();
                    String error = cause != null
                        ? cause.getMessage() : ex.getMessage();

                    JOptionPane.showMessageDialog(LoginPage.this,
                        "Database login failed:\n" + error);
                }
                finally
                {
                    loginButton.setEnabled(true);
                    loginButton.setText("LOGIN   →");
                }
            }
        };

        worker.execute();
    }

    public static void main(String[] args)
    {
        SwingUtilities.invokeLater(() -> new LoginPage());
    }
}