package oracletest;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.sql.*;
public class ForgotPassword extends JFrame {

    // ---------- Palette (taken from the forest background) ----------
    static final Color DEEP    = new Color(35, 49, 13);
    static final Color LEAF    = new Color(63, 90, 34);
    static final Color LEAF_L  = new Color(125, 159, 69);
    static final Color CREAM   = new Color(250, 238, 200);
    static final Color FIELD   = Color.WHITE;
    static final Color LINE    = new Color(203, 199, 152);
    static final Color MUTED   = new Color(107, 106, 69);
    static final Color AMBER   = new Color(200, 116, 42);
    static final Color ERR     = new Color(181, 67, 42);

    // ---------- State ----------
    private int currentStep = 1;
    private int attemptsLeft = 3;
    private String verifiedUsername, verifiedEmail, verifiedAnswer;

    // ---------- UI ----------
    private final CardLayout cards = new CardLayout();
    private final JPanel stepHolder = new JPanel(cards);
    private StepIndicator indicator;
    private JLabel subtitleLabel;

    private IconField usernameField, emailField, answerField, newPassField, confirmField;
    private JLabel msg1, msg2, msg3, questionText, matchMsg, meterLabel;
    private RoundButton continueBtn, verifyBtn, resetBtn, doneBtn;
    private StrengthBar strengthBar;
    private final JLabel[] ruleLabels = new JLabel[4];
    private final String[] ruleText = {"8+ characters", "A number", "A symbol", "Max 25 characters"};

    public ForgotPassword() {

        setTitle("Human-Animal Safety Alert and Rescue System");
        setSize(1200, 850);
        setMinimumSize(new Dimension(900, 720));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        // Background image
        java.net.URL url = ForgotPassword.class.getResource("/oracletest/form_background.png");
        if (url == null) {
            url = ForgotPassword.class.getResource("/oracletest/forest_background.png.png");
        }
        if (url == null) {
            url = ForgotPassword.class.getResource("/oracletest/forest_background.png");
        }
        final Image bg = (url != null) ? new ImageIcon(url).getImage() : null;
        if (bg == null) System.out.println("Background image not found.");

        JPanel background = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int w = getWidth(), h = getHeight();
                if (bg != null && bg.getWidth(null) > 0) {
                    // "cover" scaling keeps the picture's proportions
                    double s = Math.max((double) w / bg.getWidth(null), (double) h / bg.getHeight(null));
                    int dw = (int) (bg.getWidth(null) * s), dh = (int) (bg.getHeight(null) * s);
                    g.drawImage(bg, (w - dw) / 2, (h - dh) / 2, dw, dh, this);
                    g.setColor(new Color(10, 44, 7, 70));
                    g.fillRect(0, 0, w, h);
                } else {
                    g.setColor(new Color(35, 49, 13));
                    g.fillRect(0, 0, w, h);
                }
            }
        };
        setContentPane(background);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(new ShadowText(new Font("Arial", Font.BOLD, 29), new Color(255, 246, 222),
                "HUMAN-ANIMAL SAFETY ALERT", "& RESCUE SYSTEM"));
        content.add(Box.createVerticalStrut(4));
        content.add(new ShadowText(new Font("Arial", Font.BOLD, 17), new Color(250, 196, 28),
                "Monitor  •  Predict  •  Protect  •  Rescue"));
        content.add(Box.createVerticalStrut(22));

        // ---------- Card ----------
        RoundedPanel card = new RoundedPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(26, 30, 24, 30));
        card.setPreferredSize(new Dimension(450, 560));
        card.setMaximumSize(new Dimension(450, 560));
        card.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("RESET PASSWORD", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(LEAF);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        card.add(title);

        subtitleLabel = new JLabel("", SwingConstants.CENTER);
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subtitleLabel.setForeground(MUTED);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitleLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        card.add(subtitleLabel);
        card.add(Box.createVerticalStrut(10));

        indicator = new StepIndicator();
        card.add(indicator);
        card.add(Box.createVerticalStrut(14));

        stepHolder.setOpaque(false);
        stepHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
        stepHolder.add(buildStep1(), "s1");
        stepHolder.add(buildStep2(), "s2");
        stepHolder.add(buildStep3(), "s3");
        stepHolder.add(buildStep4(), "s4");
        card.add(stepHolder);

        content.add(card);
        background.add(content);

        showStep(1);
        setVisible(true);
    }

    // =====================================================================
    //  STEP PANELS
    // =====================================================================

    private JPanel stepPanel() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private JPanel buildStep1() {
        JPanel p = stepPanel();

        p.add(label("Username"));
        usernameField = new IconField("👤", new HintTextField("Enter your username"), false);
        p.add(usernameField);
        p.add(Box.createVerticalStrut(14));

        p.add(label("Registered email"));
        emailField = new IconField("✉️", new HintTextField("you@example.com"), false);
        p.add(emailField);
        p.add(Box.createVerticalStrut(8));

        msg1 = msgLabel();
        p.add(msg1);
        p.add(Box.createVerticalStrut(6));

        continueBtn = new RoundButton("CONTINUE", true);
        continueBtn.addActionListener(e -> doStep1());
        p.add(continueBtn);
        p.add(Box.createVerticalStrut(8));

        RoundButton back = new RoundButton("← Back to login", false);
        back.addActionListener(e -> goToLogin());
        p.add(back);

        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel buildStep2() {
        JPanel p = stepPanel();

        // Question box
        JPanel qBox = new JPanel(new BorderLayout());
        qBox.setBackground(new Color(111, 143, 63, 40));
        qBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, LEAF_L),
                new EmptyBorder(12, 14, 12, 14)));
        qBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        qBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));

        JLabel small = new JLabel("YOUR SECURITY QUESTION");
        small.setFont(new Font("Segoe UI", Font.BOLD, 11));
        small.setForeground(MUTED);
        questionText = new JLabel(" ");
        questionText.setFont(new Font("Segoe UI", Font.BOLD, 16));
        questionText.setForeground(DEEP);
        qBox.add(small, BorderLayout.NORTH);
        qBox.add(questionText, BorderLayout.CENTER);
        p.add(qBox);
        p.add(Box.createVerticalStrut(18));

        p.add(label("Your answer"));
        answerField = new IconField("🛡️", new HintPasswordField("Type your answer"), true);
        p.add(answerField);
        p.add(Box.createVerticalStrut(4));

        JLabel hint = new JLabel("Answers are not case-sensitive.");
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        hint.setForeground(AMBER);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(hint);
        p.add(Box.createVerticalStrut(6));

        msg2 = msgLabel();
        p.add(msg2);
        p.add(Box.createVerticalStrut(6));

        verifyBtn = new RoundButton("VERIFY ANSWER", true);
        verifyBtn.addActionListener(e -> doStep2());
        p.add(verifyBtn);
        p.add(Box.createVerticalStrut(8));

        RoundButton back = new RoundButton("← Back to step 1", false);
        back.addActionListener(e -> {
            answerField.clear();
            msg2.setText(" ");
            attemptsLeft = 3;
            showStep(1);
        });
        p.add(back);

        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel buildStep3() {
        JPanel p = stepPanel();

        p.add(label("New password"));
        newPassField = new IconField("🔑", new HintPasswordField("Create a new password"), true);
        p.add(newPassField);
        p.add(Box.createVerticalStrut(8));

        strengthBar = new StrengthBar();
        p.add(strengthBar);
        meterLabel = new JLabel(" ");
        meterLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        meterLabel.setForeground(MUTED);
        meterLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(Box.createVerticalStrut(3));
        p.add(meterLabel);

        JPanel rules = new JPanel(new GridLayout(2, 2, 10, 2));
        rules.setOpaque(false);
        rules.setAlignmentX(Component.LEFT_ALIGNMENT);
        rules.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        for (int i = 0; i < 4; i++) {
            ruleLabels[i] = new JLabel("○ " + ruleText[i]);
            ruleLabels[i].setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
            ruleLabels[i].setForeground(MUTED);
            rules.add(ruleLabels[i]);
        }
        p.add(rules);
        p.add(Box.createVerticalStrut(12));

        p.add(label("Confirm password"));
        confirmField = new IconField("🔒", new HintPasswordField("Re-enter the password"), true);
        p.add(confirmField);
        p.add(Box.createVerticalStrut(4));

        matchMsg = msgLabel();
        p.add(matchMsg);
        msg3 = msgLabel();
        p.add(msg3);
        p.add(Box.createVerticalStrut(4));

        resetBtn = new RoundButton("🔐 RESET PASSWORD", true);
        resetBtn.setEnabled(false);
        resetBtn.addActionListener(e -> doStep3());
        p.add(resetBtn);
        p.add(Box.createVerticalStrut(8));

        RoundButton back = new RoundButton("← Back", false);
        back.addActionListener(e -> {
            newPassField.clear();
            confirmField.clear();
            showStep(2);
        });
        p.add(back);

        p.add(Box.createVerticalGlue());

        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { checkPassword(); }
            public void removeUpdate(DocumentEvent e) { checkPassword(); }
            public void changedUpdate(DocumentEvent e) { checkPassword(); }
        };
        newPassField.input.getDocument().addDocumentListener(dl);
        confirmField.input.getDocument().addDocumentListener(dl);
        return p;
    }

    private JPanel buildStep4() {
        JPanel p = stepPanel();
        p.add(Box.createVerticalStrut(30));

        JLabel tick = new JLabel("✅", SwingConstants.CENTER);
        tick.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 56));
        tick.setAlignmentX(Component.LEFT_ALIGNMENT);
        tick.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        p.add(tick);
        p.add(Box.createVerticalStrut(10));

        JLabel t = new JLabel("Password updated", SwingConstants.CENTER);
        t.setFont(new Font("Segoe UI", Font.BOLD, 22));
        t.setForeground(LEAF);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        t.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        p.add(t);

        JLabel s = new JLabel("You can now log in with your new password.", SwingConstants.CENTER);
        s.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        s.setForeground(MUTED);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        p.add(s);
        p.add(Box.createVerticalStrut(30));

        doneBtn = new RoundButton("BACK TO LOGIN", true);
        doneBtn.addActionListener(e -> goToLogin());
        p.add(doneBtn);

        p.add(Box.createVerticalGlue());
        return p;
    }

    // =====================================================================
    //  FLOW CONTROL
    // =====================================================================

    private void showStep(int n) {
        currentStep = n;
        cards.show(stepHolder, "s" + n);
        indicator.setCurrent(n);

        String[] subs = {"", "Let’s confirm it’s really you.", "Answer your security question.",
                "Choose a new password.", "All set."};
        subtitleLabel.setText(subs[n]);

        JButton def = n == 1 ? continueBtn : n == 2 ? verifyBtn : n == 3 ? resetBtn : doneBtn;
        getRootPane().setDefaultButton(def);

        SwingUtilities.invokeLater(() -> {
            if (n == 1) usernameField.input.requestFocusInWindow();
            else if (n == 2) answerField.input.requestFocusInWindow();
            else if (n == 3) newPassField.input.requestFocusInWindow();
        });
    }

    private void goToLogin() {
        new LoginPage().setVisible(true);
        dispose();
    }

    // ---------- Step 1: load security question ----------
    private void doStep1() {
        final String username = usernameField.value().trim();
        final String email = emailField.value().trim();

        boolean okUser = !username.isEmpty();
        boolean okEmail = email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
        usernameField.setError(!okUser);
        emailField.setError(!okEmail);

        if (!okUser) { showMsg(msg1, "Please enter your username."); return; }
        if (email.isEmpty()) { showMsg(msg1, "Please enter your registered email."); return; }
        if (!okEmail) { showMsg(msg1, "Enter a valid email address."); return; }
        showMsg(msg1, " ");

        continueBtn.setEnabled(false);
        continueBtn.setText("LOOKING UP...");

        final String sql = "SELECT security_question FROM USER_ACCOUNT WHERE username = ? AND email = ?";

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                try (Connection con = DBConnection.getConnection();
                     PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, username);
                    ps.setString(2, email);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? rs.getString("security_question") : null;
                    }
                }
            }

            @Override
            protected void done() {
                try {
                    String question = get();
                    if (question != null && !question.trim().isEmpty()) {
                        verifiedUsername = username;
                        verifiedEmail = email;
                        questionText.setText("<html>" + question + "</html>");
                        answerField.clear();
                        showMsg(msg2, " ");
                        attemptsLeft = 3;
                        verifyBtn.setEnabled(true);
                        showStep(2);
                    } else {
                        showMsg(msg1, "Username and email do not match, or no security question is registered.");
                    }
                } catch (Exception ex) {
                    showMsg(msg1, "Error: " + errText(ex));
                } finally {
                    continueBtn.setEnabled(true);
                    continueBtn.setText("CONTINUE");
                }
            }
        }.execute();
    }

    // ---------- Step 2: verify the security answer ----------
    private void doStep2() {
        final String answer = answerField.value().trim();
        if (answer.isEmpty()) {
            answerField.setError(true);
            showMsg(msg2, "Please enter your answer.");
            return;
        }

        verifyBtn.setEnabled(false);
        verifyBtn.setText("CHECKING...");

        final String sql = "SELECT COUNT(*) FROM USER_ACCOUNT "
                + "WHERE username = ? AND email = ? "
                + "AND LOWER(TRIM(security_answer)) = LOWER(TRIM(?))";

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                try (Connection con = DBConnection.getConnection();
                     PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, verifiedUsername);
                    ps.setString(2, verifiedEmail);
                    ps.setString(3, answer);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() && rs.getInt(1) == 1;
                    }
                }
            }

            @Override
            protected void done() {
                boolean keepEnabled = true;
                try {
                    if (get()) {
                        verifiedAnswer = answer;
                        newPassField.clear();
                        confirmField.clear();
                        checkPassword();
                        showMsg(msg3, " ");
                        showStep(3);
                    } else {
                        attemptsLeft--;
                        answerField.setError(true);
                        answerField.clear();
                        if (attemptsLeft > 0) {
                            showMsg(msg2, "That answer doesn’t match. " + attemptsLeft
                                    + " attempt" + (attemptsLeft > 1 ? "s" : "") + " left.");
                        } else {
                            showMsg(msg2, "Too many attempts. Go back and try again.");
                            keepEnabled = false;
                        }
                    }
                } catch (Exception ex) {
                    showMsg(msg2, "Error: " + errText(ex));
                } finally {
                    verifyBtn.setEnabled(keepEnabled);
                    verifyBtn.setText("VERIFY ANSWER");
                }
            }
        }.execute();
    }

    // ---------- Step 3: live password checks ----------
    private void checkPassword() {
        if (newPassField == null || confirmField == null) return;
        String v = newPassField.value();
        String v2 = confirmField.value();

        boolean[] r = {
                v.length() >= 8,
                v.matches(".\\d."),
                v.matches(".[^A-Za-z0-9]."),
                !v.isEmpty() && v.length() <= 25
        };
        for (int i = 0; i < 4; i++) {
            ruleLabels[i].setText((r[i] ? "✓ " : "○ ") + ruleText[i]);
            ruleLabels[i].setForeground(r[i] ? LEAF : MUTED);
            ruleLabels[i].setFont(ruleLabels[i].getFont().deriveFont(r[i] ? Font.BOLD : Font.PLAIN));
        }

        int score = 0;
        if (!v.isEmpty()) {
            score = (r[0] ? 1 : 0) + (r[1] ? 1 : 0) + (r[2] ? 1 : 0) + (v.length() >= 12 ? 1 : 0);
        }
        strengthBar.setScore(score);
        meterLabel.setText(v.isEmpty() ? " " : score <= 1 ? "Weak" : score == 2 ? "Fair" : score == 3 ? "Good" : "Strong");

        boolean same = !v2.isEmpty() && v.equals(v2);
        if (v2.isEmpty()) {
            matchMsg.setText(" ");
        } else if (same) {
            matchMsg.setForeground(LEAF);
            matchMsg.setText("Passwords match ✓");
        } else {
            matchMsg.setForeground(ERR);
            matchMsg.setText("Passwords don’t match yet.");
        }

        resetBtn.setEnabled(r[0] && r[1] && r[2] && r[3] && same);
    }

    // ---------- Step 3: save the new password ----------
    private void doStep3() {
        final String password = newPassField.value();

        resetBtn.setEnabled(false);
        resetBtn.setText("SAVING...");

        // Answer is re-checked in the UPDATE itself, so the database stays the judge.
        final String sql = "UPDATE USER_ACCOUNT SET password = ? "
                + "WHERE username = ? AND email = ? "
                + "AND LOWER(TRIM(security_answer)) = LOWER(TRIM(?))";

        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                try (Connection con = DBConnection.getConnection();
                     PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, password);
                    ps.setString(2, verifiedUsername);
                    ps.setString(3, verifiedEmail);
                    ps.setString(4, verifiedAnswer);
                    return ps.executeUpdate();
                }
            }

            @Override
            protected void done() {
                try {
                    if (get() == 1) {
                        showStep(4);
                    } else {
                        showMsg(msg3, "Verification failed. Please start again.");
                    }
                } catch (Exception ex) {
                    showMsg(msg3, "Password reset failed: " + errText(ex));
                } finally {
                    resetBtn.setText("🔐 RESET PASSWORD");
                    checkPassword();
                }
            }
        }.execute();
    }

    // =====================================================================
    //  SMALL HELPERS
    // =====================================================================

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 13));
        l.setForeground(DEEP);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 6, 0));
        return l;
    }

    private JLabel msgLabel() {
        JLabel l = new JLabel(" ");
        l.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        l.setForeground(ERR);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        return l;
    }

    private void showMsg(JLabel l, String text) {
        l.setForeground(ERR);
        l.setText("<html>" + text + "</html>");
    }

    private String errText(Exception ex) {
        Throwable c = ex.getCause();
        return c != null ? c.getMessage() : ex.getMessage();
    }

    // =====================================================================
    //  CUSTOM COMPONENTS
    // =====================================================================

    private static void aa(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static Color blend(Color a, Color b, double t) {
        return new Color((int) (a.getRed() * (1 - t) + b.getRed() * t),
                (int) (a.getGreen() * (1 - t) + b.getGreen() * t),
                (int) (a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    /** Heading text with a soft shadow so it reads over the photo. */
    static class ShadowText extends JComponent {
        private final String[] lines;
        private final Color color;

        ShadowText(Font f, Color color, String... lines) {
            this.lines = lines;
            this.color = color;
            setFont(f);
            FontMetrics fm = getFontMetrics(f);
            setPreferredSize(new Dimension(700, fm.getHeight() * lines.length + 6));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, fm.getHeight() * lines.length + 6));
            setAlignmentX(Component.CENTER_ALIGNMENT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int y = fm.getAscent();
            for (String line : lines) {
                int x = (getWidth() - fm.stringWidth(line)) / 2;
                g2.setColor(new Color(5, 25, 5, 190));
                g2.drawString(line, x + 2, y + 2);
                g2.setColor(color);
                g2.drawString(line, x, y);
                y += fm.getHeight();
            }
            g2.dispose();
        }
    }

    /** Frosted cream card with rounded corners and a soft shadow. */
    static class RoundedPanel extends JPanel {
        RoundedPanel() { setOpaque(false); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            int w = getWidth(), h = getHeight();
            for (int i = 8; i >= 1; i--) {
                g2.setColor(new Color(0, 0, 0, 7));
                g2.fillRoundRect(8 - i, 10 - i, w - 16 + 2 * i, h - 16 + 2 * i, 28, 28);
            }
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(8, 4, w - 16, h - 16, 24, 24);
            g2.setColor(new Color(215, 220, 210));
            g2.drawRoundRect(8, 4, w - 17, h - 17, 24, 24);
            g2.dispose();
        }
    }

    /** Step dots 1 - 2 - 3 with labels. */
    static class StepIndicator extends JPanel {
        private int current = 1;
        private final String[] names = {"VERIFY", "QUESTION", "PASSWORD"};

        StepIndicator() {
            setOpaque(false);
            setPreferredSize(new Dimension(380, 66));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        void setCurrent(int c) { current = c; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            int gap = 110, d = 30, cy = 20;
            int mid = getWidth() / 2;
            for (int i = 0; i < 3; i++) {
                int cx = mid + (i - 1) * gap;
                boolean done = i + 1 < current;
                boolean active = i + 1 == current;

                if (i < 2) {
                    g2.setStroke(new BasicStroke(2f));
                    g2.setColor(done ? LEAF_L : LINE);
                    g2.drawLine(cx + d / 2 + 6, cy, cx + gap - d / 2 - 6, cy);
                }

                String txt = done ? "✓" : String.valueOf(i + 1);
                if (done) {
                    g2.setColor(LEAF_L);
                    g2.fillOval(cx - d / 2, cy - d / 2, d, d);
                } else if (active) {
                    g2.setColor(new Color(111, 143, 63, 60));
                    g2.fillOval(cx - d / 2 - 5, cy - d / 2 - 5, d + 10, d + 10);
                    g2.setColor(LEAF);
                    g2.fillOval(cx - d / 2, cy - d / 2, d, d);
                } else {
                    g2.setStroke(new BasicStroke(2f));
                    g2.setColor(LINE);
                    g2.drawOval(cx - d / 2, cy - d / 2, d, d);
                }

                g2.setFont(new Font("Segoe UI Emoji", Font.BOLD, 14));
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor((done || active) ? Color.WHITE : MUTED);
                g2.drawString(txt, cx - fm.stringWidth(txt) / 2, cy + fm.getAscent() / 2 - 1);

                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                fm = g2.getFontMetrics();
                g2.setColor(active ? LEAF : MUTED);
                g2.drawString(names[i], cx - fm.stringWidth(names[i]) / 2, cy + d / 2 + 18);
            }
            g2.dispose();
        }
    }

    /** Rounded input with an icon, focus highlight, error state and optional show/hide eye. */
    static class IconField extends JPanel {
        final JTextField input;
        private boolean focused, error;

        IconField(String icon, JTextField input, boolean eye) {
            this.input = input;
            setOpaque(false);
            setLayout(new BorderLayout(8, 0));
            setBorder(new EmptyBorder(0, 12, 0, 8));
            setPreferredSize(new Dimension(380, 46));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel ic = new JLabel(icon);
            ic.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
            ic.setForeground(MUTED);

            input.setBorder(null);
            input.setOpaque(false);
            input.setFont(new Font("Segoe UI", Font.PLAIN, 15));
            input.setForeground(DEEP);
            input.setCaretColor(DEEP);
            input.addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { focused = true; repaint(); }
                @Override public void focusLost(FocusEvent e) { focused = false; repaint(); }
            });
            input.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { setError(false); }
                public void removeUpdate(DocumentEvent e) { setError(false); }
                public void changedUpdate(DocumentEvent e) { }
            });

            add(ic, BorderLayout.WEST);
            add(input, BorderLayout.CENTER);

            if (eye && input instanceof JPasswordField) {
                final JPasswordField pf = (JPasswordField) input;
                final char echo = pf.getEchoChar();
                JButton b = new JButton("👁️");
                b.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 15));
                b.setBorderPainted(false);
                b.setContentAreaFilled(false);
                b.setFocusable(false);
                b.setCursor(new Cursor(Cursor.HAND_CURSOR));
                b.setToolTipText("Show / hide");
                b.addActionListener(e -> pf.setEchoChar(pf.getEchoChar() == 0 ? echo : (char) 0));
                add(b, BorderLayout.EAST);
            }
        }

        String value() {
            return input instanceof JPasswordField
                    ? new String(((JPasswordField) input).getPassword())
                    : input.getText();
        }

        void clear() { input.setText(""); setError(false); }

        void setError(boolean e) {
            if (error != e) { error = e; repaint(); }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            int w = getWidth(), h = getHeight();
            if (focused) {
                g2.setColor(new Color(111, 143, 63, 60));
                g2.fillRoundRect(0, 0, w, h, 14, 14);
            }
            g2.setColor(FIELD);
            g2.fillRoundRect(2, 2, w - 4, h - 4, 10, 10);
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(error ? ERR : focused ? LEAF_L : LINE);
            g2.drawRoundRect(2, 2, w - 5, h - 5, 10, 10);
            g2.dispose();
        }
    }

    static class HintTextField extends JTextField {
        private final String hint;
        HintTextField(String hint) { this.hint = hint; }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty() && !hasFocus()) paintHint(this, g, hint);
        }
    }

    static class HintPasswordField extends JPasswordField {
        private final String hint;
        HintPasswordField(String hint) { this.hint = hint; }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getPassword().length == 0 && !hasFocus()) paintHint(this, g, hint);
        }
    }

    private static void paintHint(JTextField f, Graphics g, String hint) {
        Graphics2D g2 = (Graphics2D) g.create();
        aa(g2);
        g2.setColor(new Color(163, 159, 120));
        g2.setFont(f.getFont());
        FontMetrics fm = g2.getFontMetrics();
        Insets in = f.getInsets();
        g2.drawString(hint, in.left, (f.getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }

    /** Green gradient button (primary) or plain text link (secondary). */
    static class RoundButton extends JButton {
        private final boolean primary;

        RoundButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setFont(new Font(primary ? "Segoe UI Emoji" : "Segoe UI", primary ? Font.BOLD : Font.PLAIN, 15));
            int h = primary ? 46 : 32;
            setPreferredSize(new Dimension(380, h));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            int w = getWidth(), h = getHeight();
            boolean en = isEnabled();
            boolean hover = getModel().isRollover() && en;
            boolean press = getModel().isPressed() && en;

            if (primary) {
                Color top = hover ? LEAF_L.brighter() : LEAF_L;
                Color bot = hover ? LEAF.brighter() : LEAF;
                if (!en) { top = blend(top, CREAM, .55); bot = blend(bot, CREAM, .55); }
                g2.setPaint(new GradientPaint(0, 0, top, 0, h, bot));
                g2.fillRoundRect(0, press ? 1 : 0, w, h - 1, 12, 12);
                g2.setColor(en ? Color.WHITE : new Color(255, 255, 255, 210));
            } else {
                g2.setColor(hover ? DEEP : LEAF);
            }

            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            String t = getText();
            int tx = (w - fm.stringWidth(t)) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(t, tx, ty);
            if (!primary && hover) g2.drawLine(tx, ty + 3, tx + fm.stringWidth(t), ty + 3);
            g2.dispose();
        }
    }

    /** Red -> amber -> green strength bar. */
    static class StrengthBar extends JComponent {
        private int score;

        StrengthBar() {
            setPreferredSize(new Dimension(380, 8));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        void setScore(int s) { score = s; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            int w = getWidth(), h = getHeight();
            g2.setColor(LINE);
            g2.fillRoundRect(0, 0, w, h, h, h);
            if (score > 0) {
                g2.setColor(score <= 1 ? ERR : score == 2 ? AMBER : LEAF_L);
                g2.fillRoundRect(0, 0, w * score / 4, h, h, h);
            }
            g2.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ForgotPassword());
    }
}