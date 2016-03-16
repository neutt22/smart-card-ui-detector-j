package com.awb.ovejera.jim;

import com.mysql.jdbc.exceptions.jdbc4.CommunicationsException;
import com.mysql.jdbc.exceptions.jdbc4.MySQLNonTransientException;
import net.miginfocom.swing.MigLayout;

import javax.smartcardio.*;
import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class Main extends JFrame implements ActionListener, KeyListener {

    private SwingWorker cardWorker = new SwingWorker<Void, String>() {

        private AWBConnection awb_connection = new AWBConnection();
        Connection conn = null;

        @Override
        protected Void doInBackground() throws Exception {

            try {

                // Get the 1st terminal
                terminals = factory.terminals().list();
                ct = terminals.get(0);

                while(true) {

                    // Try to connect to database
                    conn = awb_connection.connect();

                    // APPLICATION READY
                    lblStatus.setText("TAP YOUR CARD");
                    statusFx.start();

                    // Wait for card
                    ct.waitForCardPresent(0);

                    // Notify user to wait for DB transaction
                    lblStatus.setText("PLEASE WAIT...");

                    // Initiate request transaction
                    readCard();

//                    conn.close();
                }
            }catch (CardException ce){
                ce.printStackTrace();
                lblStatus.setText("<html><span style='font-size:50px; color:red;'>PLEASE CHECK TERMINAL THEN RESTART</span></html>");
                statusFx.stop();
                lblStatus.setVisible(true);
            }catch (CommunicationsException ce){
                ce.printStackTrace();
                lblStatus.setText("<html><span style='font-size:50px; color:red;'>PLEASE CHECK CONNECTION THEN RESTART</span></html>");
                statusFx.stop();
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
            statusFx.stop();

            txtId.setText(chunks.get(0));
            txtName.setText(chunks.get(1));
            txtTower.setText(chunks.get(2));
            txtUnit.setText(chunks.get(3));
            txtCStatus.setText(chunks.get(4));
        }

        private void readCard(){
            try{
                if(ct != null){
                    c = ct.connect("*");
                }else{
                    System.out.println("No smart card terminal.");
                    System.exit(0);
                }

                cc = c.getBasicChannel();

                ResponseAPDU answer = cc.transmit(commandApdu);
                System.out.println(answer.toString());
                byte[] reponseBytesArr=answer.getBytes();
                StringBuilder sb = new StringBuilder();
                for(int i=0;i<reponseBytesArr.length;i++){

                    byte b =reponseBytesArr[i];

                    if(i <= reponseBytesArr.length - 3){

//                    sb.append(String.format("%02X ", b));
                        sb.append((int)b & 0xFF);
                    }
                }
                System.out.println("UID: " + sb.toString());

                // UID is fetched, query against database
                List<String> member = awb_connection.member(Integer.parseInt(sb.toString()));
                awb_connection.log(Integer.parseInt(sb.toString()));
//                List<String> member = awb_connection.member(Integer.parseInt("9988"));

                if(member.size() <= 0){
                    lblStatus.setText("<html><span style='font-size:50px; color:red;'>NO DATA FOUND</span></html>");
                    ct.waitForCardAbsent(0);

                    // Application is ready
                    lblStatus.setText("<html><span style='font-size:50px; color:gray;'>TAP YOUR CARD</span></html>");
                    statusFx.start();
                    return;
                }else{
                    publish(member.get(0));
                    publish(member.get(1));
                    publish(member.get(2));
                    publish(member.get(3));
                    publish(member.get(4));
                }

                ct.waitForCardAbsent(0);

                // Ready for next transaction
                lblStatus.setText("<html><span style='font-size:50px; color:gray;'>TAP YOUR CARD</span></html>");
                statusFx.start();

                // Initiate info remover
                infoRemover.start();
            }catch (CommunicationsException ce){
                System.out.println("Communications exception caught");
                lblStatus.setText("<html><span style='font-size:50px; color:red;'>PLEASE CHECK CONNECTION</span></html>");
            }catch (MySQLNonTransientException e){
                System.out.println("MySQL Non-transient connection exception caught");
//                e.printStackTrace();
            }catch (CardException ce){
                lblStatus.setText("<html><span style='font-size:50px; color:red;'>PLEASE CHECK TERMINAL THEN RESTART</span></html>");
                ce.printStackTrace();
            }catch (ClassNotFoundException cnfe){
                System.out.println("No driver found");
                lblStatus.setText("<html><span style='font-size:50px; color:red;'>NO DRIVER FOUND. PLEASE CONTACT DEVELOPER.</span></html>");
            }catch (SQLException sqle){
                sqle.printStackTrace();
                System.out.println("SQL exception caught");
                lblStatus.setText("<html><span style='font-size:50px; color:red;'>SQL ERROR</span></html>");
            }
        }
    };

    // CARD STATUS FX
    private  boolean hidden = false;
    private Timer statusFx = new Timer(500, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            lblStatus.setVisible(hidden = !hidden);
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

    // TODO: Set to 60000 in production mode
    // SCREEN INFO REMOVER
    private Timer infoRemover = new Timer(1000, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            txtId.setText("00000000");
            txtName.setText("n/a");
            txtTower.setText("n/a");
            txtUnit.setText("n/a");
            txtCStatus.setText("n/a");

            // Stop removing
            infoRemover.stop();
        }
    });

    public void actionPerformed(ActionEvent ae){
        System.exit(0);
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
    private ImageIcon avatarIcon = new ImageIcon(Main.class.getResource("/res/avatar.png"));
    private ImageIcon mezzaIcon = new ImageIcon(Main.class.getResource("/res/mezza-logo.png"));
    private ImageIcon awbIcon = new ImageIcon(Main.class.getResource("/res/awb-logo-dev.png"));

    // HEADER
    private JLabel lblMezzaIcon = new JLabel("", mezzaIcon, JLabel.LEFT);
    private JLabel lblClock = new JLabel("Clock initializing...", null, JLabel.RIGHT);

    // LEFT PANE LABEL
    private JLabel lblAvatarIcon = new JLabel("", avatarIcon, JLabel.CENTER);
    private JLabel _lblId = new JLabel("#");
    private JLabel txtId = new JLabel("0001");
    private JLabel lblPoweredBy = new JLabel("powered by ");
    private JLabel lblAwbIcon = new JLabel("", awbIcon, JLabel.CENTER);

    // RIGHT PANE LABELS
    private JLabel _lblTower = new JLabel("TOWER");
    private JLabel _lblUnit = new JLabel("UNIT");
    private JPanel infoPane = new JPanel(new MigLayout("insets 10", "[grow]", "[grow]"));


    // RIGHT PANE DYNAMIC TEXT
    private JLabel txtName = new JLabel("Juan Dela Cruz");
    private JLabel txtCStatus = new JLabel("Owner");
    private JLabel txtTower = new JLabel("4");
    private JLabel txtUnit = new JLabel("2020");
    private JTextArea txtInfo = new JTextArea("Inform tenant to approach admin office immediately!");


    // FOOTER PANE DYNAMIC LABELS
    private JLabel lblStatus = new JLabel("TAP YOUR CARD", null, JLabel.CENTER);

    public Main(){
        super("AWB");

        lblAvatarIcon.setBorder(LineBorder.createGrayLineBorder());
        lblClock.setFont(new Font("Arial", Font.PLAIN, 45));
        lblClock.setForeground(Color.decode("#f2f2f2"));

        _lblId.setForeground(Color.decode("#666666"));
        _lblId.setFont(new Font("Arial", Font.BOLD, 45));
        txtId.setFont(new Font("Arial", Font.BOLD, 45));
        txtId.setForeground(Color.decode("#666666"));
        lblPoweredBy.setFont(new Font("Arial", Font.PLAIN, 20));
        lblPoweredBy.setForeground(Color.decode("#666666"));

        txtName.setForeground(Color.decode("#414141"));
        txtName.setFont(new Font("Arial", Font.BOLD, 70));
        txtCStatus.setFont(new Font("Arial", Font.PLAIN, 45));
        txtCStatus.setForeground(Color.decode("#666666"));
        _lblTower.setForeground(Color.decode("#666666"));
        _lblTower.setFont(new Font("Arial", Font.PLAIN, 45));
        _lblUnit.setForeground(Color.decode("#666666"));
        _lblUnit.setFont(new Font("Arial", Font.PLAIN, 45));
        txtTower.setForeground(Color.decode("#434343"));
        txtTower.setFont(new Font("Arial", Font.BOLD, 45));
        txtUnit.setForeground(Color.decode("#434343"));
        txtUnit.setFont(new Font("Arial", Font.BOLD, 45));
        txtInfo.setOpaque(false);
        txtInfo.setForeground(Color.decode("#ff0000"));
        txtInfo.setFont(new Font("Arial", Font.PLAIN, 30));
        txtInfo.setBorder(BorderFactory.createEmptyBorder());
        txtInfo.setLineWrap(true);
        txtInfo.setWrapStyleWord(true);
        txtInfo.setEditable(false);
        infoPane.setOpaque(false);
        infoPane.setBorder(BorderFactory.createLineBorder(Color.decode("#ff0000"), 2));

        headerPane.setBackground(Color.decode("#1c4587"));
        headerPane.add(lblMezzaIcon, "w 50%");
        headerPane.add(lblClock, "w 50%, gapright 2%");

        leftPane.add(lblAvatarIcon, "center, wrap, bottom, gaptop 5%");
        leftPane.add(_lblId, "center, span, split, top");
        leftPane.add(txtId, "wrap, top");
        leftPane.add(lblPoweredBy, "span, split, center, gaptop 3%");
        leftPane.add(lblAwbIcon, "bottom, gapbottom 2%");

        rightPane.add(txtName, "center, gaptop 5%, span, bottom, wrap");
        rightPane.add(new JSeparator(), "center, top, w 80%, span 2 1, split, flowy");
        rightPane.add(txtCStatus, "center, wrap");
        rightPane.add(_lblTower, "center, bottom");
        rightPane.add(_lblUnit, "center, bottom, wrap");
        rightPane.add(txtTower, "center, top");
        rightPane.add(txtUnit, "center, top, wrap");
        rightPane.add(Box.createHorizontalBox(), "grow, span, wrap");
        rightPane.add(Box.createHorizontalBox(), "grow, span, wrap");
        rightPane.add(Box.createHorizontalBox(), "grow, span, wrap");
        infoPane.add(txtInfo, "grow");
        rightPane.add(infoPane, "growy, center, bottom, gapbottom 2%, span, w 80%");

        mainPane.setBackground(Color.decode("#efefef"));
        mainPane.add(headerPane, "span, grow, dock north");
        mainPane.add(leftPane, "grow, w 50%, h 100%");
        mainPane.add(rightPane, "grow, w 50%, wrap");

        add(mainPane);

        setUndecorated(true);
        setExtendedState(MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setVisible(true);

        clockFx.start();

//        cardWorker.execute();

        txtId.addKeyListener(this);
        txtName.addKeyListener(this);
        txtTower.addKeyListener(this);
        txtCStatus.addKeyListener(this);
        txtUnit.addKeyListener(this);
        addKeyListener(this);
        setFocusable(true);

    }

    @Override
    public void keyTyped(KeyEvent ke) {

    }

    @Override
    public void keyPressed(KeyEvent ke) {
        if(! ke.isControlDown()) return;

        if(ke.getKeyCode() == KeyEvent.VK_N){
            new NewEntry().setVisible(true);
        }else if(ke.getKeyCode() == KeyEvent.VK_E){
            new EditEntry().setVisible(true);
        }else if(ke.getKeyCode() == KeyEvent.VK_P){
            // TODO: Dump log table to Excel sheet
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {

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