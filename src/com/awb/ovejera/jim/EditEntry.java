package com.awb.ovejera.jim;

import com.mysql.jdbc.exceptions.jdbc4.CommunicationsException;
import com.mysql.jdbc.exceptions.jdbc4.MySQLNonTransientException;
import net.miginfocom.swing.MigLayout;

import javax.smartcardio.*;
import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.text.LabelView;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;

import static com.awb.ovejera.jim.Main.LOGGER;

public class EditEntry extends JFrame implements ActionListener {

    private int uid;

    private SwingWorker cardWorker = new SwingWorker<Void, String>() {

        private AWBConnection awb_connection = new AWBConnection();
        Connection conn = null;

        @Override
        protected Void doInBackground() throws Exception {

            try {

                // Get the 1st terminal
                terminals = factory.terminals().list();
                ct = terminals.get(0);

                while(Main.active) {

                    // Try to connect to database
                    conn = awb_connection.connect();

                    // APPLICATION READY
                    lblStatus.setForeground(Color.decode("#666666"));
                    lblStatus.setText("TAP YOUR CARD");

                    LOGGER.fine("Waiting for card present...");

                    // Wait for card
                    ct.waitForCardPresent(0);

                    // Admin has closed this window, do not continue
                    if(!Main.active) break;

                    // Notify user to wait for DB transaction
                    lblStatus.setForeground(Color.decode("#666666"));
                    lblStatus.setText("PLEASE WAIT...");

                    LOGGER.fine("Reading a card...");

                    // Initiate request transaction
                    readCard();
                }
            }catch (CardException ce){
                LOGGER.log(Level.SEVERE, "No terminal found", ce);
                lblStatus.setForeground(Color.red);
                lblStatus.setText("PLEASE CHECK TERMINAL THEN RESTART");
            }catch (CommunicationsException ce){
                LOGGER.log(Level.SEVERE, "Communications Exception", ce);
                lblStatus.setForeground(Color.red);
                lblStatus.setText("PLEASE CHECK CONNECTION THEN RESTART");
            }finally {
                conn.close();
            }

            return null;
        }

        @Override
        protected void process(List<String> chunks){
            lblStatus.setForeground(Color.decode("#666666"));
            lblStatus.setText("PLEASE RELEASE THE CARD");

            txtId.setText(String.format("%04d", Integer.parseInt(chunks.get(0))));
            txtName.setText(chunks.get(1));
            txtTower.setText(chunks.get(2));
            txtUnit.setText(chunks.get(3));
            txtCStatus.setText(chunks.get(4));
            txtInfo.setText(chunks.get(5));

            // Valid search, enable buttons
            btnSave.setEnabled(true);
            btnDelete.setEnabled(true);

            lblStatus.setForeground(Color.decode("#666666"));
            lblStatus.setText("RECORD FOUND");

            LOGGER.fine("Finished.");
        }

        private void readCard(){
            try{
                if(ct != null){
                    c = ct.connect("*");
                }else{
                    LOGGER.log(Level.SEVERE, "No terminal found!");
                }


                cc = c.getBasicChannel();

                ResponseAPDU answer = cc.transmit(commandApdu);
                System.out.println(answer.toString());
                byte[] reponseBytesArr = answer.getBytes();
                StringBuilder sb = new StringBuilder();
                for(int i = 0; i < reponseBytesArr.length; i++){

                    byte b = reponseBytesArr[i];

                    if(i <= reponseBytesArr.length - 3){

                        sb.append((int)b & 0xFF);
                    }
                }

                LOGGER.fine("UID: " + sb.toString());

                // UID is fetched, query against database
                List<String> member = awb_connection.member(Integer.parseInt(sb.toString()));

                if(member.size() <= 0){
                    lblStatus.setForeground(Color.red);
                    lblStatus.setText("NO DATA FOUND");
                    clear();
                    ct.waitForCardAbsent(0);

                    // Application is ready
                    lblStatus.setForeground(Color.decode("#666666"));
                    lblStatus.setText("TAP YOUR CARD");

                    return;
                }else{

                    LOGGER.fine("Displaying found member...");

                    publish(member.get(0));
                    publish(member.get(1));
                    publish(member.get(2));
                    publish(member.get(3));
                    publish(member.get(4));
                    publish(member.get(5));
                }

                ct.waitForCardAbsent(0);

                // Ready for next transaction
                lblStatus.setForeground(Color.decode("#666666"));
                lblStatus.setText("TAP YOUR CARD");

            }catch (CommunicationsException ce){
                LOGGER.log(Level.SEVERE, "No connection", ce);
                lblStatus.setText("PLEASE CHECK CONNECTION");
                clear();
            }catch (MySQLNonTransientException e){
                LOGGER.log(Level.SEVERE, "MySQLNonTransientException", e);
                clear();
            }catch (CardException ce){
                LOGGER.log(Level.SEVERE, "CardException", ce);
                lblStatus.setText("PLEASE CHECK TERMINAL THEN RESTART");
                clear();
            }catch (ClassNotFoundException cnfe){
                LOGGER.log(Level.SEVERE, "ClassNotFoundException", cnfe);
                lblStatus.setText("NO DRIVER FOUND. PLEASE CONTACT DEVELOPER.");
                clear();
            }catch (SQLException sqle){
                LOGGER.log(Level.SEVERE, "SQLException", sqle);
                lblStatus.setText("SQL ERROR");
                clear();
            }
        }
    };

    // Save the record
    private void save(){
        if(
            txtId.getText().length() <= 0 ||
            txtName.getText().length() <= 0 ||
            txtTower.getText().length() <= 0 ||
            txtUnit.getText().length() <= 0 ||
            txtCStatus.getText().length() <= 0
        ){
            lblStatus.setForeground(Color.red);
            lblStatus.setText("INCOMPLETE DETAILS");
        }else{
            try{

                LOGGER.fine("Updating record...");

                AWBConnection awb_connection = new AWBConnection();
                Connection conn = awb_connection.connect();

                // Get the UID
                int mezza_id = Integer.parseInt(txtId.getText());

                String name = txtName.getText();
                String tower = txtTower.getText();
                String unit = txtUnit.getText();
                String cStatus = txtCStatus.getText();
                String info = txtInfo.getText();

                boolean update = awb_connection.update(mezza_id, name, tower, unit, cStatus, info);

                LOGGER.info("Update result: " + update);

                if(update) {
                    lblStatus.setForeground(Color.decode("#666666"));
                    lblStatus.setText("RECORD UPDATED");
                }

                conn.close();

            }catch (Exception e){
                lblStatus.setText("<html><span style='font-size:50px; color:red;'>FAILED TO UPDATE RECORD</span></html>");
                e.printStackTrace();
            }
        }
    }

    private void delete(){
        try{
            AWBConnection awb_connection = new AWBConnection();
            awb_connection.connect();

            // Get the UID
            uid = Integer.parseInt(txtId.getText());

            boolean delete = awb_connection.delete(uid);

            if(delete) lblStatus.setText("<html><span style='font-size:50px; color:green;'>RECORD DELETED</span></html>");

            clear();

        }catch (Exception e){
            lblStatus.setText("<html><span style='font-size:50px; color:red;'>FAILED TO DELETE</span></html>");
            e.printStackTrace();
        }
    }

    @Override
    public void actionPerformed(ActionEvent ae){
        String action = ae.getActionCommand();

        if(action.equals("save")) save();
        else if(action.equals("close")) {
            LOGGER.fine("Destroying this window...");
            Main.active = false;
            dispose();
            LOGGER.fine("EditEntry destroyed.");
        }
        else if(action.equals("delete")) delete();

    }

    private ActionListener searchListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            LOGGER.fine("Searching Mezza ID...");
            try{
                AWBConnection awb_connection = new AWBConnection();
                Connection conn = awb_connection.connect();

                // UID is fetched, query against database
                List<String> member = awb_connection.mezzaMember(Integer.parseInt(txtId.getText()));

                if(member.size() <= 0){
                    lblStatus.setForeground(Color.red);
                    lblStatus.setText("NO DATA FOUND");

                    conn.close();

                    clear();

                    return;
                }else{
                    txtId.setText(String.format("%04d", Integer.parseInt(member.get(0))));
                    txtName.setText(member.get(1));
                    txtTower.setText(member.get(2));
                    txtUnit.setText(member.get(3));
                    txtCStatus.setText(member.get(4));
                    txtInfo.setText(member.get(5));

                    // Valid search, enable buttons
                    btnSave.setEnabled(true);
                    btnDelete.setEnabled(true);

                    lblStatus.setForeground(Color.decode("#666666"));
                    lblStatus.setText("RECORD FOUND");
                }

                conn.close();
            }catch (NumberFormatException nfe){
                LOGGER.warning("Invalid Mezza ID data type");
                lblStatus.setForeground(Color.red);
                lblStatus.setText("INVALID MEZZA ID");

                clear();
            }
            catch (Exception ee){
                ee.printStackTrace();
            }
        }
    };

    private void clear(){
        txtId.setText("0000");
        txtName.setText("-");
        txtTower.setText("-");
        txtUnit.setText("-");
        txtCStatus.setText("-");
        txtInfo.setText("");

        btnSave.setEnabled(false);
        btnDelete.setEnabled(false);
    }

    private CommandAPDU commandApdu = new CommandAPDU(new byte[] { (byte)0xFF, (byte)0xCA, (byte)0x00, (byte)0x00, (byte)0x00 } );
    private TerminalFactory factory = TerminalFactory.getDefault();
    private List<CardTerminal> terminals;
    private CardTerminal ct;
    private Card c;
    private CardChannel cc;


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
    private JLabel lblClock = new JLabel("Edit & Search Records", null, JLabel.RIGHT);

    // LEFT PANE LABEL
    private JLabel lblAvatarIcon = new JLabel("", avatarIcon, JLabel.CENTER);
    private JLabel _lblId = new JLabel("#");
    private JTextField txtId = new JTextField("0001");
    private JLabel lblPoweredBy = new JLabel("powered by ");
    private JLabel lblAwbIcon = new JLabel("", awbIcon, JLabel.CENTER);
    private JLabel lblStatus = new JLabel("TAP YOUR CARD");

    // RIGHT PANE LABELS
    private JLabel _lblTower = new JLabel("TOWER");
    private JLabel _lblUnit = new JLabel("UNIT");
    private JPanel infoPane = new JPanel(new MigLayout("insets 10", "[grow]", "[grow]"));

    // RIGHT PANE DYNAMIC TEXT
    private JTextArea txtName = new JTextArea("-");
    private JTextField txtCStatus = new JTextField("Owner");
    private JTextField txtTower = new JTextField("-");
    private JTextField txtUnit = new JTextField("-");
    private JTextArea txtInfo = new JTextArea("No info");

    private ImageIcon checkIcon = new ImageIcon(Main.class.getResource("/res/check.png"));
    private ImageIcon closeIcon = new ImageIcon(Main.class.getResource("/res/close.png"));
    private ImageIcon deleteIcon = new ImageIcon(Main.class.getResource("/res/delete.png"));

    private JButton btnSave = new JButton("", checkIcon);
    private JButton btnClose = new JButton("", closeIcon);
    private JButton btnDelete = new JButton("", deleteIcon);


    public EditEntry(){
        super("AWB - Edit Entry");

        lblAvatarIcon.setBorder(LineBorder.createGrayLineBorder());
        lblClock.setFont(new Font("Arial", Font.PLAIN, 45));
        lblClock.setForeground(Color.decode("#f2f2f2"));

        _lblId.setForeground(Color.decode("#666666"));
        _lblId.setFont(new Font("Arial", Font.BOLD, 45));
        txtId.setFont(new Font("Arial", Font.BOLD, 45));
        txtId.setForeground(Color.decode("#666666"));
        txtId.setBackground(Color.decode("#f7f5f5"));
        txtId.setHorizontalAlignment(JTextField.CENTER);
        txtId.setEditable(true);
        lblStatus.setFont(new Font("Arial", Font.BOLD, 40));
        lblStatus.setForeground(Color.decode("#666666"));
        lblPoweredBy.setFont(new Font("Arial", Font.PLAIN, 20));
        lblPoweredBy.setForeground(Color.decode("#666666"));

        txtName.setForeground(Color.decode("#414141"));
        txtName.setFont(new Font("Arial", Font.BOLD, 70));
        txtName.setBackground(Color.decode("#f7f5f5"));
        txtName.setLineWrap(true);
        txtName.setWrapStyleWord(true);
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

        rightPane.add(txtName, "center, growx, gaptop 5%, span, bottom, wrap");
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
        rightPane.add(btnDelete, "span, split");
        rightPane.add(btnClose);

        mainPane.setBackground(Color.decode("#efefef"));
        mainPane.add(headerPane, "span, grow, dock north");
        mainPane.add(leftPane, "grow, w 50%, h 100%");
        mainPane.add(rightPane, "grow, w 50%, wrap");

        add(mainPane);

        setUndecorated(true);
        setExtendedState(MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        // Press enter on the ID field
        txtId.addActionListener(searchListener);

        btnClose.setActionCommand("close");
        btnClose.addActionListener(this);

        btnSave.setActionCommand("save");
        btnSave.addActionListener(this);

        // Disable delete and update button by default
        btnDelete.setEnabled(false);
        btnSave.setEnabled(false);

        cardWorker.execute();

    }

}