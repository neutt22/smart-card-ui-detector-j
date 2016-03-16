package com.awb.ovejera.jim;

import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Date;

public class IdleWindow extends JFrame {

    // CARD STATUS FX
    private  boolean hidden = false;
    private Timer statusFx = new Timer(600, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            _lblMessage.setVisible(hidden = !hidden);
        }
    });

    // CLOCK FX
    private SimpleDateFormat format = new SimpleDateFormat("hh:mm:ss a MMMM dd, YYYY");
    private Timer clockFx = new Timer(1000, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            lblClock.setText(format.format(new Date()));
        }
    });

    private JPanel mainPane = new JPanel(new MigLayout("insets 0 0 0 0", "[grow]", "[grow]"));
    private JPanel headerPane = new JPanel(new MigLayout(",insets 0 0 0 0", "[grow]", "[grow]"));

    // IMAGES
    private ImageIcon mezzaIcon = new ImageIcon(Main.class.getResource("/res/mezza-logo.png"));

    // HEADER
    private JLabel lblMezzaIcon = new JLabel("", mezzaIcon, JLabel.LEFT);
    private JLabel lblClock = new JLabel("Clock initializing...", null, JLabel.RIGHT);

    // BODY
    private JLabel _lblMessage = new JLabel("Waiting for next tenant...");

    public IdleWindow(){
        super("Idle");

        lblClock.setFont(new Font("Arial", Font.PLAIN, 45));
        lblClock.setForeground(Color.decode("#f2f2f2"));

        _lblMessage.setFont(new Font("Arial", Font.BOLD, 80));
        _lblMessage.setForeground(Color.decode("#434343"));

        headerPane.setBackground(Color.decode("#1c4587"));
        headerPane.add(lblMezzaIcon, "w 50%");
        headerPane.add(lblClock, "w 50%, gapright 2%");

        mainPane.setBackground(Color.decode("#95b1df"));
        mainPane.add(headerPane, "span, grow, dock north, wrap");
        mainPane.add(_lblMessage, "center, span");

        add(mainPane);

        setUndecorated(true);
        setExtendedState(MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

        clockFx.start();
        statusFx.start();
    }
}
