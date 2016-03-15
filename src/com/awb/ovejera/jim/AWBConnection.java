package com.awb.ovejera.jim;

import sun.jdbc.odbc.JdbcOdbcPlatform;

import javax.naming.CommunicationException;
import javax.swing.*;
import java.io.IOException;
import java.rmi.server.ExportException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class AWBConnection {

    private String url = "jdbc:mysql://localhost/db_awb?user=awb_user&password=awb_pass";

    private Connection connection;
    private PreparedStatement preparedStatement;
    private ResultSet resultSet;//mezza 1

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss a");

    public AWBConnection(){

    }


    public Connection connect() throws SQLException, ClassNotFoundException{

        Class.forName("com.mysql.jdbc.Driver");
        connection = DriverManager.getConnection(url);

        return connection;
    }

    public boolean create(int uid, String name, String tower, String unit, String cStatus){

        String sql = "insert into db_awb.members (uid, name, tower, unit, status) values(?,?,?,?,?)";

        if(exists(uid)){
            JOptionPane.showMessageDialog(null, "This card already has an entry. Please use different card.", "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        try{
            preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setInt(1, uid);
            preparedStatement.setString(2, name);
            preparedStatement.setString(3, tower);
            preparedStatement.setString(4, unit);
            preparedStatement.setString(5, cStatus);

            preparedStatement.executeUpdate();

            preparedStatement.close();

            return true;

        }catch (SQLException sqle){
            JOptionPane.showMessageDialog(null, "Error in saving new record", "Error", JOptionPane.ERROR_MESSAGE);
            sqle.printStackTrace();
        }

        return false;
    }

    public boolean update(int uid, String name, String tower, String unit, String cStatus){

        String sql = "update db_awb.members set name=?, tower=?, unit=?, status=? where uid=?";

        try {
            preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, name);
            preparedStatement.setString(2, tower);
            preparedStatement.setString(3, unit);
            preparedStatement.setString(4, cStatus);
            preparedStatement.setInt(5, uid);

            int res = preparedStatement.executeUpdate();

            return res == 1;
        }catch (SQLException sqle){
            sqle.printStackTrace();
        }

        return false;
    }

    public boolean delete(int uid){

        String sql = "delete from db_awb.members where uid=?";

        try {
            preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setInt(1, uid);

            int res = preparedStatement.executeUpdate();

            return res == 1;
        }catch (SQLException sqle){
            sqle.printStackTrace();
        }
        return false;
    }

    private boolean exists(int uid){

        String sql = "select uid from db_awb.members where uid=?";

        try {
            preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setInt(1, uid);

            resultSet = preparedStatement.executeQuery();

            resultSet.last();

            int count = resultSet.getRow();

            preparedStatement.close();

            // Has old entry
            return count != 0;

        }catch (SQLException sqle){
            sqle.printStackTrace();
        }

        return false;
    }

    public List<String> member(int uid) throws SQLException, ClassNotFoundException{
        List<String> members = new ArrayList<String>();

        String sql;
        sql = "select * from db_awb.members where uid=?";

        preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setInt(1, uid);
        resultSet = preparedStatement.executeQuery();

        while(resultSet.next()){
            members.add(resultSet.getString("uid"));
            members.add(resultSet.getString("name"));
            members.add(resultSet.getString("tower"));
            members.add(resultSet.getString("unit"));
            members.add(resultSet.getString("status"));
        }

        preparedStatement.close();

        return members;
    }

    public boolean log(int uid) throws SQLException, ClassNotFoundException{

        String sql = "insert into db_awb.logs (uid, log_date) values(?,now())";

        preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setInt(1, uid);

        // Successful = 1
        int res = preparedStatement.executeUpdate();

        preparedStatement.close();

        return res == 1;
    }


    public static void main(String args[]){
//        AWBConnection connection = new AWBConnection();
//        Connection conn = connection.connect();
//
//        List<String> member = connection.member(999);
//
//        System.out.println(member.get(0));
//        System.out.println(member.get(1));
//        System.out.println(member.get(2));
//        System.out.println(member.get(3));
//
//        try {
//            conn.close();
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
        
    }
}