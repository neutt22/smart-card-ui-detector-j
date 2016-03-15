package com.awb.ovejera.jim;

import net.miginfocom.swing.MigLayout;

import javax.smartcardio.*;
import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.util.List;

public class NewEntry extends JFrame implements ActionListener {

    private int uid;

    private SwingWorker cardWorker = new SwingWorker<Void, String>() {


        Connection conn = null;

        @Override
        protected Void doInBackground() throws Exception {

            try {

                // Get the 1st terminal
                terminals = factory.terminals().list();
                ct = terminals.get(0);

                while(true) {

                    // APPLICATION READY
                    lblStatus.setText("<html><span style='font-size:50px; color:gray;'>TAP YOUR CARD</span></html>");


                    // Wait for card
                    ct.waitForCardPresent(0);

                    // Notify user to wait for DB transaction
                    lblStatus.setText("<html><span style='font-size:50px; color:gray;'>PLEASE WAIT...</span></html>");

                    // Initiate request transaction
                    readCard();

//                    conn.close();
                }
            }catch (CardException ce){
                ce.printStackTrace();
                lblStatus.setText("<html><span style='font-size:50px; color:red;'>PLEASE CHECK TERMINAL THEN RESTART</span></html>");
                lblStatus.setVisible(true);
            }finally {
                conn.close();
            }

            return null;
        }

        @Override
        protected void process(List<String> chunks){
            lblStatus.setVisible(true);
            lblStatus.setText("<html><span style='font-size:50px; color:gray;'>PLEASE RELEASE THE CARD</span></html>");

            // Display UID
            txtId.setText(chunks.get(0));
        }

        public void readCard(){
            try{
                if(ct != null){
                    c = ct.connect("*");
                }else{
                    System.out.println("No smart card terminal.");
                }

                cc = c.getBasicChannel();

                ResponseAPDU answer = cc.transmit(commandApdu);
                System.out.println(answer.toString());
                byte[] reponseBytesArr = answer.getBytes();
                StringBuilder sb = new StringBuilder();
                for(int i = 0; i < reponseBytesArr.length; i++){

                    byte b = reponseBytesArr[i];

                    if(i <= reponseBytesArr.length - 3){

//                    sb.append(String.format("%02X ", b));
                        sb.append((int)b & 0xFF);
                    }
                }
                System.out.println("UID: " + sb.toString());

                publish(sb.toString());

                uid = Integer.parseInt(sb.toString());

                ct.waitForCardAbsent(0);

                // Ready for next transaction
                lblStatus.setText("<html><span style='font-size:50px; color:gray;'>TAP YOUR CARD</span></html>");

            }catch (CardException ce){
                lblStatus.setText("<html><span style='font-size:50px; color:red;'>PLEASE CHECK TERMINAL THEN RESTART</span></html>");
                ce.printStackTrace();
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
            lblStatus.setText("<html><span style='font-size:50px; color:red;'>INCOMPLETE DETAILS</span></html>");
        }else{
            try{
                AWBConnection awb_connection = new AWBConnection();
                Connection conn = awb_connection.connect();

                String name = txtName.getText();
                String tower = txtTower.getText();
                String unit = txtUnit.getText();
                String cStatus = txtCStatus.getText();

                boolean create = awb_connection.create(uid, name, tower, unit, cStatus);

                if(create) lblStatus.setText("<html><span style='font-size:50px; color:green;'>NEW RECORD ADDED</span></html>");

                conn.close();

            }catch (Exception e){
                lblStatus.setText("<html><span style='font-size:50px; color:red;'>NEW RECORD FAILED</span></html>");
                e.printStackTrace();
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

    private JPanel mainPane = new JPanel(new MigLayout("insets 0 0 0 0", "[grow]", "[grow]")){
        @Override
        protected void paintComponent(Graphics grphcs) {
            super.paintComponent(grphcs);
            Graphics2D g2d = (Graphics2D) grphcs;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
//            GradientPaint gp = new GradientPaint(0, 0, getBackground().brighter().brighter(), 0, getHeight(), getBackground().darker().darker());
            GradientPaint gp = new GradientPaint(0, 0, new Color(153, 204, 255), 0, getHeight(), new Color(179, 217, 255));
            g2d.setPaint(gp);
            g2d.fillRect(0, 0, getWidth(), getHeight());

        }
    };
    private JPanel rightPane = new JPanel(new MigLayout(", insets 0 0 0 10", "[grow]", "[grow]")){
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
            setOpaque(false);
        }
    };

    // IMAGES
    private ImageIcon avatarIcon = new ImageIcon(Main.class.getResource("/res/avatar.jpg"));
    private ImageIcon mezzaIcon = new ImageIcon(Main.class.getResource("/res/mezza-2-logo.jpg"));
    private ImageIcon awbIcon = new ImageIcon(Main.class.getResource("/res/awb-logo-dev.png"));
    private ImageIcon checkIcon = new ImageIcon(Main.class.getResource("/res/check.png"));
    private ImageIcon closeIcon = new ImageIcon(Main.class.getResource("/res/close.png"));

    // LEFT PANE LABEL
    private JLabel lblAvatarIcon = new JLabel("", avatarIcon, JLabel.CENTER);
    private JLabel lblMezzaIcon = new JLabel("", mezzaIcon, JLabel.CENTER);

    // RIGHT PANE LABELS
    private JLabel _lblId = new JLabel("<html><span style='font-size:38px; color:gray;'>ID:</span></html>");
    private JLabel _lblName = new JLabel("<html><span style='font-size:38px; color:gray;'>NAME:</span></html>");
    private JLabel _lblTower = new JLabel("<html><span style='font-size:38px; color:gray;'>TOWER:</span></html>");
    private JLabel _lblUnit = new JLabel("<html><span style='font-size:38px; color:gray;'>UNIT:</span></html>");
    private JLabel _lblCStatus = new JLabel("<html><span style='font-size:38px; color:gray;'>STATUS:</span></html>");

    // RIGHT PANE DYNAMIC TEXT
    private JTextField txtId = new JTextField();
    private JTextField txtName = new JTextField();
    private JTextField txtTower = new JTextField();
    private JTextField txtUnit = new JTextField();
    private JTextField txtCStatus = new JTextField();

    // FOOTER PANE
    private JPanel footerPane = new JPanel(new MigLayout("", "[grow]", "[grow]"));
    private JLabel lblStatus = new JLabel("<html><span style='font-size:50px; color:gray;'>INITIALIZING...</span></html>");
    private JLabel lblAwbIcon = new JLabel("", awbIcon, JLabel.CENTER);
    private JButton btnSave = new JButton("", checkIcon);
    private JButton btnClose = new JButton("", closeIcon);

    public NewEntry(){
        super("AWB - New Entry");

        leftPane.add(lblMezzaIcon, "wrap, top, left");
        leftPane.add(lblAvatarIcon, "wrap, top, center");

        // Right components styles
        txtId.setFont(new Font("Arial", Font.PLAIN, 42));
        txtId.setBorder(LineBorder.createBlackLineBorder());
        txtId.setEditable(false);
        txtName.setFont(new Font("Arial", Font.PLAIN, 42));
        txtName.setBorder(LineBorder.createBlackLineBorder());
        txtTower.setFont(new Font("Arial", Font.PLAIN, 42));
        txtTower.setBorder(LineBorder.createBlackLineBorder());
        txtUnit.setFont(new Font("Arial", Font.PLAIN, 42));
        txtUnit.setBorder(LineBorder.createBlackLineBorder());
        txtCStatus.setFont(new Font("Arial", Font.PLAIN, 42));
        txtCStatus.setBorder(LineBorder.createBlackLineBorder());

        rightPane.add(_lblId, "wrap");
        rightPane.add(txtId, "wrap, gapbottom 5%, w 250");
        rightPane.add(_lblName, "wrap");
        rightPane.add(txtName, "span, wrap, gapbottom 5%, growx");
        rightPane.add(_lblTower);
        rightPane.add(_lblUnit, "wrap");
        rightPane.add(txtTower, "gapbottom 5%, top, growx");
        rightPane.add(txtUnit, "gapbottom 5%, wrap, top, w 250");
        rightPane.add(_lblCStatus, "wrap");
        rightPane.add(txtCStatus, "w 250");

        footerPane.setBackground(Color.white);
        btnSave.setFont(new Font("Arial", Font.PLAIN, 20));
        btnClose.setFont(new Font("Arial", Font.PLAIN, 20));
        btnSave.setActionCommand("save");
        btnClose.setActionCommand("close");
        btnSave.addActionListener(this);
        btnClose.addActionListener(this);
        footerPane.add(lblStatus, "gapleft 1%");
        footerPane.add(lblAwbIcon, "right, wrap, span 1 2");
        footerPane.add(btnSave, "growy, split, w 250");
        footerPane.add(btnClose, "growy, w 250");

        mainPane.add(leftPane, "grow, w 40%");
        mainPane.add(rightPane, "grow, w 60%, wrap");
        mainPane.add(footerPane, "grow, span");
        add(mainPane);

        setUndecorated(true);
        setExtendedState(MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        // Press enter on the status field
        txtCStatus.addActionListener(this);

        cardWorker.execute();

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