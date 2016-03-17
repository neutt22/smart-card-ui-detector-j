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
import java.util.logging.*;

public class Main extends JFrame implements ActionListener, KeyListener {

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

                    // Try to connect to database
                    conn = awb_connection.connect();

                    // APPLICATION READY
//                    lblStatus.setForeground(Color.decode("#666666"));
//                    lblStatus.setText("TAP YOUR CARD");

                    // Wait for card
                    ct.waitForCardPresent(0);

                    // Stop info remover, idle window from popping
                    infoRemover.stop();

                    // Notify user to wait for DB transaction
                    lblStatus.setForeground(Color.decode("#666666"));
                    lblStatus.setText("PLEASE WAIT...");

                    LOGGER.fine("Reading a card...");

                    // Initiate request transaction
                    readCard();

//                    conn.close();
                }
            }catch (CardException ce){
                lblStatus.setForeground(Color.red);
                lblStatus.setText("PLEASE CHECK TERMINAL THEN RESTART");
                LOGGER.log(Level.SEVERE, "Card exception", ce);
            }catch (CommunicationsException ce){
                lblStatus.setForeground(Color.red);
                lblStatus.setText("PLEASE CHECK CONNECTION THEN RESTART");
                LOGGER.log(Level.SEVERE, "Communications exception", ce);
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

            idleWindow.setVisible(false);
        }

        private void readCard(){
            try{
                if(ct != null){
                    c = ct.connect("*");
                }else{
                    LOGGER.severe("No smart card terminal. System exiting.");
                    System.exit(0);
                }

                cc = c.getBasicChannel();

                ResponseAPDU answer = cc.transmit(commandApdu);
                LOGGER.info("Response: " + answer.toString());
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

                // UID is fetched, query against database
                List<String> member = awb_connection.member(Integer.parseInt(sb.toString()));

                if(member.size() <= 0){
                    lblStatus.setForeground(Color.red);
                    lblStatus.setText("NO DATA FOUND");
                    LOGGER.fine("Member size = 0");
                    ct.waitForCardAbsent(0);

                    // Application is ready
                    lblStatus.setForeground(Color.decode("#666666"));
                    lblStatus.setText("TAP YOUR CARD");
                    return;
                }else{
                    publish(member.get(0));
                    publish(member.get(1));
                    publish(member.get(2));
                    publish(member.get(3));
                    publish(member.get(4));
                    publish(member.get(5));

                    awb_connection.log(Integer.parseInt(sb.toString()));

                    LOGGER.fine("Tap event logged.");
                    LOGGER.fine("Member list published");
                }

                ct.waitForCardAbsent(0);

                // Ready for next transaction
                lblStatus.setForeground(Color.decode("#666666"));
                lblStatus.setText("TAP YOUR CARD");

                LOGGER.info("Initiating info remover and idle window...");

                // Initiate info remover
                infoRemover.start();

            }catch (CommunicationsException ce){
                LOGGER.log(Level.SEVERE, "Communications exception", ce);
                lblStatus.setForeground(Color.red);
                lblStatus.setText("PLEASE CHECK CONNECTION");
            }catch (MySQLNonTransientException e){
                LOGGER.log(Level.SEVERE, "MySQL Non-transient connection exception caught", e);
            }catch (CardException ce){
                lblStatus.setForeground(Color.red);
                lblStatus.setText("PLEASE CHECK TERMINAL THEN RESTART");
                LOGGER.log(Level.SEVERE, "Card exception", ce);
            }catch (ClassNotFoundException cnfe){
                LOGGER.log(Level.SEVERE, "Class not found exception. No driver found.", cnfe);
                lblStatus.setForeground(Color.red);
                lblStatus.setText("NO DRIVER FOUND. PLEASE CONTACT DEVELOPER.");
            }catch (SQLException sqle){
                LOGGER.log(Level.SEVERE, "Mysql exception", sqle);
                lblStatus.setForeground(Color.red);
                lblStatus.setText("SQL ERROR");
            }
        }
    };

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
    private Timer infoRemover = new Timer(5000, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {

            // Show the idle window
            idleWindow.setVisible(true);

            txtId.setText("0000");
            txtName.setText("n/a");
            txtTower.setText("n/a");
            txtUnit.setText("n/a");
            txtCStatus.setText("n/a");
            txtInfo.setText("");

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

    private IdleWindow idleWindow = new IdleWindow();

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
    private JLabel txtId = new JLabel("0000");
    private JLabel lblPoweredBy = new JLabel("powered by ");
    private JLabel lblAwbIcon = new JLabel("", awbIcon, JLabel.CENTER);

    // RIGHT PANE LABELS
    private JLabel _lblTower = new JLabel("TOWER");
    private JLabel _lblUnit = new JLabel("UNIT");
    private JPanel infoPane = new JPanel(new MigLayout("insets 10", "[grow]", "[grow]"));


    // RIGHT PANE DYNAMIC TEXT
    private JLabel txtName = new JLabel("Juan Dela Cruz");
    private JLabel txtCStatus = new JLabel("Owner");
    private JLabel txtTower = new JLabel("1");
    private JLabel txtUnit = new JLabel("1000");
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
        lblStatus.setFont(new Font("Arial", Font.BOLD, 30));
        lblStatus.setForeground(Color.decode("#666666"));
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
        leftPane.add(lblStatus, "span, center, wrap");
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

        cardWorker.execute();

        txtId.addKeyListener(this);
        txtName.addKeyListener(this);
        txtTower.addKeyListener(this);
        txtCStatus.addKeyListener(this);
        txtUnit.addKeyListener(this);
        addKeyListener(this);
        setFocusable(true);

        idleWindow.setVisible(true);

        LOGGER.fine("UI constructed");

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

    public final static Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String args[]){

        setupLogger(LOGGER);

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

    private static void setupLogger(Logger logger){

        logger.setUseParentHandlers(false);

        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.FINEST);
        logger.addHandler(handler);
        logger.setLevel(Level.FINEST);
    }
}