package oracletest;



import javax.swing.*;

import java.awt.*;

import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;



public class WelcomePage extends JFrame {



    BufferedImage bgImg;



    JPanel bg;



    JButton btnStart, btnExit;



    int W = 1672;

    int H = 901;



    public WelcomePage() {



        setTitle("Human-Animal Safety Alert & Rescue System");



        // Load background image

        try {

            bgImg = ImageIO.read(

                getClass().getResource("/oracletest/forest_background.png.jpgg.png.jpeg")

            );

        } catch (Exception e) {

            JOptionPane.showMessageDialog(this,

                "Image not found! Check forest_background.png");

            return;

        }



        // Background panel

        bg = new JPanel(null) {



            protected void paintComponent(Graphics g) {



                super.paintComponent(g);



                // FIX: use a COPY of the graphics so the scale

                // does not affect how the buttons are painted

                Graphics2D g2 = (Graphics2D) g.create();



                g2.scale(getWidth() / (double) W,

                         getHeight() / (double) H);



                g2.setRenderingHint(

                    RenderingHints.KEY_ANTIALIASING,

                    RenderingHints.VALUE_ANTIALIAS_ON

                );



                // Background image

                g2.drawImage(bgImg, 0, 0, W, H, this);



                // Green title box

                g2.setColor(new Color(0, 35, 18));



                g2.fillRoundRect(60, 195, 880, 535, 40, 40);



                // Gold border

                g2.setColor(new Color(255, 210, 30));



                g2.setStroke(new BasicStroke(3));



                g2.drawRoundRect(60, 195, 880, 535, 40, 40);



                // Logo circle

                g2.setColor(new Color(255, 235, 190));



                g2.fillOval(410, 145, 210, 210);



                g2.setColor(new Color(255, 200, 25));



                g2.setStroke(new BasicStroke(6));



                g2.drawOval(410, 145, 210, 210);



                drawLogo(g2);



                // Main title

                g2.setColor(new Color(255, 245, 220));



                g2.setFont(new Font("Georgia", Font.BOLD, 68));



                center(g2, "HUMAN-ANIMAL", 500, 430);



                // Second title

                g2.setColor(new Color(255, 195, 20));



                g2.setFont(new Font("Georgia", Font.BOLD, 34));



                center(g2,

                    "SAFETY ALERT & RESCUE SYSTEM",

                    500, 485);



                // Divider

                g2.setColor(new Color(255, 205, 20));



                g2.setStroke(new BasicStroke(4));



                g2.drawLine(150, 520, 480, 520);



                g2.drawLine(570, 520, 850, 520);



                // Paw decoration

                g2.fillOval(505, 505, 17, 17);



                g2.fillOval(520, 495, 17, 17);



                g2.fillOval(535, 505, 17, 17);



                g2.fillOval(520, 520, 18, 15);



                // Slogan

                g2.setColor(new Color(255, 245, 220));



                g2.setFont(new Font("Georgia", Font.BOLD, 29));



                center(g2,

                    "Protecting people. Protecting wildlife.",

                    500, 585);



                // Footer

                g2.setColor(new Color(0, 45, 25));



                g2.fillRect(0, 850, W, 51);



                g2.setColor(Color.WHITE);



                g2.setFont(new Font("Serif",

                    Font.BOLD | Font.ITALIC, 24));



                center(g2,

                    "-----------------Wildlife Awareness • Early alerts • Safe rescue-----------------",

                    W / 2, 882);



                // FIX: release the copy

                g2.dispose();

            }

        };



        bg.setPreferredSize(new Dimension(1200, 647));



        setContentPane(bg);



        // Create buttons

        btnStart = new JButton("GET STARTED");



        btnExit = new JButton("EXIT");



        // Button styling

        style(btnStart,

            new Color(0, 55, 25),

            Color.WHITE);



        style(btnExit,

            new Color(255, 225, 160),

            new Color(0, 65, 30));



        // Add buttons

        bg.add(btnStart);



        bg.add(btnExit);



        // Reposition buttons when window is resized

        bg.addComponentListener(new java.awt.event.ComponentAdapter() {



            public void componentResized(

                java.awt.event.ComponentEvent e) {



                setButtons();

            }

        });



        // GET STARTED button

        btnStart.addActionListener(e -> {



            dispose();



            new LoginPage();



        });



        // EXIT button

        btnExit.addActionListener(e -> {



            System.exit(0);



        });



        // Window settings

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);



        setResizable(true);



        pack();



        setMinimumSize(new Dimension(1000, 600));



        setLocationRelativeTo(null);



        setVisible(true);



        setButtons();

    }



    // Draw shield and paw logo

    void drawLogo(Graphics2D g) {



        g.setColor(new Color(0, 65, 35));



        int[] x = {515, 590, 590, 515, 440, 440};



        int[] y = {175, 180, 255, 335, 255, 180};



        g.fillPolygon(x, y, 6);



        g.setColor(new Color(255, 195, 20));



        g.setStroke(new BasicStroke(5));



        g.drawPolygon(x, y, 6);



        // Paw print

        g.setColor(new Color(255, 235, 190));



        g.fillOval(475, 220, 22, 25);



        g.fillOval(500, 205, 22, 25);



        g.fillOval(525, 205, 22, 25);



        g.fillOval(550, 220, 22, 25);



        g.fillOval(480, 245, 85, 55);

    }



    // Center text

    void center(Graphics2D g, String s, int x, int y) {



        g.drawString(

            s,

            x - g.getFontMetrics().stringWidth(s) / 2,

            y

        );

    }



    // Button design

    void style(JButton b, Color bgColor, Color textColor) {



        b.setFont(new Font("Arial", Font.BOLD, 25));



        b.setBackground(bgColor);



        b.setForeground(textColor);



        b.setOpaque(true);



        b.setFocusPainted(false);



        b.setBorder(BorderFactory.createLineBorder(

            new Color(255, 205, 20), 4

        ));



        b.setCursor(new Cursor(Cursor.HAND_CURSOR));

    }



    // Button positions

    void setButtons() {



        double sx = bg.getWidth() / (double) W;



        double sy = bg.getHeight() / (double) H;



        btnStart.setBounds(

            (int)(190 * sx),

            (int)(620 * sy),

            (int)(325 * sx),

            (int)(80 * sy)

        );



        btnExit.setBounds(

            (int)(550 * sx),

            (int)(620 * sy),

            (int)(280 * sx),

            (int)(80 * sy)

        );

    }



    public static void main(String[] args) {



        SwingUtilities.invokeLater(() -> {



            new WelcomePage();



        });

    }

}