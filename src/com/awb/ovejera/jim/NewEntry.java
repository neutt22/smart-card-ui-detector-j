package com.awb.ovejera.jim;

import net.miginfocom.swing.MigLayout;

import javax.smartcardio.*;
import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.sql.Connection;
import java.util.List;
import java.util.logging.Level;

import static com.awb.ovejera.jim.Main.LOGGER;

public class NewEntry extends JFrame implements ActionListener {

    private int uid;

    private class RowCounterWorker extends SwingWorker<Void, Integer>{
        @Override
        protected Void doInBackground() throws Exception {

            AWBConnection awbConnection = new AWBConnection();
            Connection connection = awbConnection.connect();

            int rowCount = awbConnection.rowCount();

            publish(rowCount + 1);

            connection.close();

            LOGGER.fine("Row count finished: " + rowCount);

            return null;
        }

        @Override
        protected void process(List<Integer> chunks){
            txtId.setText(String.format("%04d", chunks.get(0)));

            LOGGER.fine("Latest Mezza ID: #" + String.format("%04d", chunks.get(0)));

            LOGGER.fine("Enabling save button...");
            btnSave.setEnabled(true);
        }
    }

    private SwingWorker cardWorker = new SwingWorker<Void, String>() {

        private AWBConnection awb_connection = new AWBConnection();
        private Connection conn = null;

        @Override
        protected Void doInBackground() throws Exception {

            try {

                // Get the 1st terminal
                terminals = factory.terminals().list();
                ct = terminals.get(0);

                while(true) {

                    conn = awb_connection.connect();

                    // APPLICATION READY
                    lblStatus.setForeground(Color.decode("#666666"));
                    lblStatus.setText("TAP YOUR CARD");


                    // Wait for card
                    ct.waitForCardPresent(0);

                    // Notify user to wait for DB transaction
                    lblStatus.setForeground(Color.decode("#666666"));
                    lblStatus.setText("PLEASE WAIT...");

                    LOGGER.fine("Reading a card...");

                    // Initiate request transaction
                    readCard();

                    conn.close();
                }
            }catch (CardException ce){
                lblStatus.setForeground(Color.red);
                lblStatus.setText("PLEASE CHECK TERMINAL THEN RESTART");
                LOGGER.log(Level.SEVERE, "Please check terminal then restart", ce);
            }finally {
                conn.close();
            }

            return null;
        }

        public void readCard(){
            try{
                if(ct != null){
                    c = ct.connect("*");
                }else{
                    LOGGER.log(Level.SEVERE, "No smart card terminal");
                }

                cc = c.getBasicChannel();

                ResponseAPDU answer = cc.transmit(commandApdu);
                byte[] reponseBytesArr = answer.getBytes();
                StringBuilder sb = new StringBuilder();
                for(int i = 0; i < reponseBytesArr.length; i++){

                    byte b = reponseBytesArr[i];

                    if(i <= reponseBytesArr.length - 3){

//                    sb.append(String.format("%02X ", b));
                        sb.append((int)b & 0xFF);
                    }
                }

                LOGGER.fine("UID: " + sb.toString());

                uid = Integer.parseInt(sb.toString());

                // Check if UID already exists
                boolean uidExists = awb_connection.exists(uid);

                if(uidExists){
                    LOGGER.fine("UID already exists, do not create a new member");

                    lblStatus.setForeground(Color.red);
                    lblStatus.setText("ID ALREADY DEFINED");
                }else{
                    LOGGER.fine("New UID detected, create a member");

                    LOGGER.fine("Get a new Mezza ID");
                    new RowCounterWorker().execute();

                    lblStatus.setForeground(Color.decode("#666666"));
                    lblStatus.setText("RELEASE THE CARD");
                }

                ct.waitForCardAbsent(0);

                // Ready for next transaction
                lblStatus.setForeground(Color.decode("#666666"));
                lblStatus.setText("TAP YOUR CARD");

            }catch (CardException ce){
                lblStatus.setForeground(Color.red);
                lblStatus.setText("PLEASE CHECK TERMINAL THEN RESTART");
                LOGGER.log(Level.SEVERE, "Please check terminal then restart", ce);
            }
        }
    };

    // Save the record
    private void save(){
        if(
            txtName.getText().length() <= 0 || txtTower.getText().length() <= 0 ||
            txtUnit.getText().length() <= 0 || txtCStatus.getText().length() <= 0
        ){
            lblStatus.setForeground(Color.red);
            lblStatus.setText("INCOMPLETE DETAILS");
        }else{
            try{
                AWBConnection awb_connection = new AWBConnection();
                Connection conn = awb_connection.connect();

                String name = txtName.getText();
                String tower = txtTower.getText();
                String unit = txtUnit.getText();
                String cStatus = txtCStatus.getText();

                LOGGER.info("Creating new member...");

                boolean create = awb_connection.create(uid, Integer.parseInt(txtId.getText()), name, tower, unit, cStatus, txtInfo.getText());

                if(create){
                    LOGGER.info("Creating new member success!");

                    lblStatus.setForeground(Color.decode("#666666"));
                    lblStatus.setText("NEW RECORD ADDED");

                    // Clear the fields
                    LOGGER.info("Clearing fields for new registration...");
                    txtId.setText("0000");
                    txtName.setText("-");
                    txtTower.setText("-");
                    txtUnit.setText("-");
                    txtCStatus.setText("-");
                    txtInfo.setText("");

                    btnSave.setEnabled(false);
                }else{
                    LOGGER.severe("Failed in creating new member");

                    lblStatus.setForeground(Color.red);
                    lblStatus.setText("FAILED TO REGISTER");
                }

                conn.close();

            }catch (Exception e){
                lblStatus.setForeground(Color.red);
                lblStatus.setText("NEW RECORD FAILED");
                LOGGER.log(Level.SEVERE, "Error creating new member", e);
            }
        }
    }

    public void actionPerformed(ActionEvent ae){
        String action = ae.getActionCommand();

        if(action.equals("save")) save();
        else if(action.equals("close")) dispose();
        else save();

    }

    private CommandAPDU commandApdu = new CommandAPDU(new byte[] { (byte)0xFF, (byte)0xCA, (byte)0x00, (byte)0x00, (byte)0x00 } );
    private TerminalFactory factory = TerminalFactory.getDefault();
    private List<CardTerminal> terminals;
    private CardTerminal ct;
    private Card c;
    private CardChannel cc;


    // Max name calculator
    private KeyListener maxNameListener = new KeyListener() {
        @Override
        public void keyTyped(KeyEvent e) {

        }

        @Override
        public void keyPressed(KeyEvent e) {

        }

        @Override
        public void keyReleased(KeyEvent e) {

            String name = txtName.getText();

            int len = name.length();

            if(len > 20){
                String newName = name.substring(0, 20);
                txtName.setText(newName);
            }else{
                String newLen = len + "/" + MAX_NAME_LENGHT;
                lblMaxLength.setText(newLen);
            }

//            len = txtName.getText().length();

        }
    };


    private JPanel mainPane = new JPanel(new MigLayout("insets 0 0 0 0", "[grow]", "[grow]")){
        @Override
        protected void paintComponent(Graphics grphcs) {
            super.paintComponent(grphcs);
//            Graphics2D g2d = (Graphics2D) grphcs;
//            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
//            GradientPaint gp = new GradientPaint(0, 0, new Color(153, 204, 255), 0, getHeight(), new Color(153, 204, 255));
//            g2d.setPaint(gp);
//            g2d.fillRect(0, 0, getWidth(), getHeight());

        }
    };
    private JPanel headerPane = new JPanel(new MigLayout(",insets 0 0 0 0", "[grow]", "[grow]"));
    private JPanel rightPane = new JPanel(new MigLayout(", insets 0 0 0 0", "[grow]", "[grow]")){
        @Override
        protected void paintComponent(Graphics g){
            super.paintComponent(g);
            setOpaque(false);
        }
    };
    private JPanel leftPane = new JPanel(new MigLayout(", insets 0 0 0 0", "[grow]", "[grow]")){

        @Override
        protected void paintComponent(Graphics g){
            super.paintComponent(g);
            setBackground(Color.white);
        }
    };

    // IMAGES
    private ImageIcon avatarIcon = new ImageIcon(Main.class.getResource("/res/avatar-new.png"));
    private ImageIcon mezzaIcon = new ImageIcon(Main.class.getResource("/res/mezza-logo.png"));
    private ImageIcon awbIcon = new ImageIcon(Main.class.getResource("/res/awb-logo-dev.png"));

    // HEADER
    private JLabel lblMezzaIcon = new JLabel("", mezzaIcon, JLabel.LEFT);
    private JLabel lblClock = new JLabel("New Record", null, JLabel.RIGHT);

    // LEFT PANE LABEL
    private JLabel lblAvatarIcon = new JLabel("", avatarIcon, JLabel.CENTER);
    private JLabel _lblId = new JLabel("#");
    private JTextField txtId = new JTextField("0000");
    private JLabel lblStatus = new JLabel("TAP YOUR CARD", null, JLabel.CENTER);
    private JLabel lblPoweredBy = new JLabel("powered by ");
    private JLabel lblAwbIcon = new JLabel("", awbIcon, JLabel.CENTER);

    // RIGHT PANE LABELS
    private JLabel _lblMaxLength = new JLabel("Max length: ");
    private JLabel _lblTower = new JLabel("TOWER");
    private JLabel _lblUnit = new JLabel("UNIT");
    private JPanel infoPane = new JPanel(new MigLayout("insets 10", "[grow]", "[grow]"));

    // RIGHT PANE DYNAMIC TEXT
    private static final int MAX_NAME_LENGHT = 20;
    private JLabel lblMaxLength = new JLabel("0/20");
    private JTextField txtName = new JTextField("");
    private JTextField txtCStatus = new JTextField("Owner");
    private JTextField txtTower = new JTextField("1");
    private JTextField txtUnit = new JTextField("1000");
    private JTextArea txtInfo = new JTextArea("Write something here...");

    private ImageIcon checkIcon = new ImageIcon(Main.class.getResource("/res/check.png"));
    private ImageIcon closeIcon = new ImageIcon(Main.class.getResource("/res/close.png"));

    private JButton btnSave = new JButton("", checkIcon);
    private JButton btnClose = new JButton("", closeIcon);

    // TODO: Count table rows as default ID
    // TODO: Remove name limiter
    public NewEntry(){
        super("AWB - New Entry");

        lblAvatarIcon.setBorder(LineBorder.createGrayLineBorder());
        lblClock.setFont(new Font("Arial", Font.PLAIN, 45));
        lblClock.setForeground(Color.decode("#f2f2f2"));

        _lblId.setForeground(Color.decode("#666666"));
        _lblId.setFont(new Font("Arial", Font.BOLD, 45));
        txtId.setFont(new Font("Arial", Font.BOLD, 45));
        txtId.setForeground(Color.decode("#666666"));
        txtId.setBackground(Color.decode("#f7f5f5"));
        txtId.setHorizontalAlignment(JTextField.CENTER);
        txtId.setEditable(false);
        lblStatus.setFont(new Font("Arial", Font.BOLD, 40));
        lblStatus.setForeground(Color.decode("#666666"));
        lblPoweredBy.setFont(new Font("Arial", Font.PLAIN, 20));
        lblPoweredBy.setForeground(Color.decode("#666666"));

        _lblMaxLength.setFont(new Font("Arial", Font.BOLD, 15));
        _lblMaxLength.setForeground(Color.decode("#666666"));
        lblMaxLength.setFont(new Font("Arial", Font.BOLD, 15));
        lblMaxLength.setForeground(Color.decode("#666666"));
        txtName.addKeyListener(maxNameListener);
        txtName.setForeground(Color.decode("#414141"));
        txtName.setFont(new Font("Arial", Font.BOLD, 70));
        txtName.setBackground(Color.decode("#f7f5f5"));
        txtName.setHorizontalAlignment(JTextField.CENTER);
        txtCStatus.setFont(new Font("Arial", Font.PLAIN, 45));
        txtCStatus.setForeground(Color.decode("#666666"));
        txtCStatus.setBackground(Color.decode("#f7f5f5"));
        txtCStatus.setHorizontalAlignment(JTextField.CENTER);
        _lblTower.setForeground(Color.decode("#666666"));
        _lblTower.setFont(new Font("Arial", Font.PLAIN, 45));
        _lblUnit.setForeground(Color.decode("#666666"));
        _lblUnit.setFont(new Font("Arial", Font.PLAIN, 45));
        txtTower.setForeground(Color.decode("#434343"));
        txtTower.setFont(new Font("Arial", Font.BOLD, 45));
        txtTower.setBackground(Color.decode("#f7f5f5"));
        txtTower.setHorizontalAlignment(JTextField.CENTER);
        txtUnit.setForeground(Color.decode("#434343"));
        txtUnit.setFont(new Font("Arial", Font.BOLD, 45));
        txtUnit.setBackground(Color.decode("#f7f5f5"));
        txtUnit.setHorizontalAlignment(JTextField.CENTER);
        txtInfo.setOpaque(false);
        txtInfo.setForeground(Color.decode("#ff0000"));
        txtInfo.setFont(new Font("Arial", Font.PLAIN, 30));
        txtInfo.setLineWrap(true);
        txtInfo.setWrapStyleWord(true);
        txtInfo.setEditable(true);
        infoPane.setOpaque(false);
        infoPane.setBorder(BorderFactory.createLineBorder(Color.decode("#ff0000"), 2));

        headerPane.setBackground(Color.decode("#1c4587"));
        headerPane.add(lblMezzaIcon, "w 50%");
        headerPane.add(lblClock, "w 50%, gapright 2%");

        leftPane.add(lblAvatarIcon, "center, wrap, bottom, gaptop 5%");
        leftPane.add(_lblId, "center, span, split, top");
        leftPane.add(txtId, "wrap, w 20%, top");
        leftPane.add(lblStatus, "span, center, wrap");
        leftPane.add(lblPoweredBy, "span, split, center, gaptop 3%");
        leftPane.add(lblAwbIcon, "bottom, gapbottom 2%");

        rightPane.add(_lblMaxLength, "span 2, split, bottom");
        rightPane.add(lblMaxLength, "wrap, bottom");
        rightPane.add(txtName, "center, growx, gaptop 5%, span, bottom, wrap");
//        rightPane.add(new JSeparator(), "center, top, w 80%, span 2 1, split, flowy");
        rightPane.add(txtCStatus, "center, w 35%, wrap, top, span");
        rightPane.add(_lblTower, "center, bottom");
        rightPane.add(_lblUnit, "center, bottom, wrap");
        rightPane.add(txtTower, "center, w 7%, top");
        rightPane.add(txtUnit, "center, w 15%, top, wrap");
        rightPane.add(Box.createHorizontalBox(), "grow, span, wrap");
        rightPane.add(Box.createHorizontalBox(), "grow, span, wrap");
        infoPane.add(txtInfo, "grow");
        rightPane.add(infoPane, "growy, center, bottom, gapbottom 2%, span, w 80%");
        rightPane.add(btnSave, "span, split, right");
        rightPane.add(btnClose);

        mainPane.setBackground(Color.decode("#efefef"));
        mainPane.add(headerPane, "span, grow, dock north");
        mainPane.add(leftPane, "grow, w 50%, h 100%");
        mainPane.add(rightPane, "grow, w 50%, wrap");

        add(mainPane);

        setUndecorated(true);
        setExtendedState(MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        LOGGER.fine("Registration UI constructed");

        btnSave.setActionCommand("save");
        btnSave.addActionListener(this);
        btnClose.setActionCommand("close");
        btnClose.addActionListener(this);

        cardWorker.execute();

        btnSave.setEnabled(false);

    }



    public static void main(String args[]){
        SwingUtilities.invokeLater(new Runnable(){
            public void run(){
                JFrame.setDefaultLookAndFeelDecorated(true);
                JDialog.setDefaultLookAndFeelDecorated(true);
                try{
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                }catch (Exception e){ e.printStackTrace(); }

                new Main();
            }
        });
    }

}