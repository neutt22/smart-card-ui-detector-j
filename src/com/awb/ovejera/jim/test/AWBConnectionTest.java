package com.awb.ovejera.jim.test;

import com.awb.ovejera.jim.AWBConnection;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class AWBConnectionTest extends TestCase {

    private AWBConnection awbConnection;
    private Connection connection;

    @Before
    public void setUp() throws Exception {
        awbConnection = new AWBConnection();
        connection = awbConnection.connect();
    }

    @After
    public void tearDown() throws Exception {
        connection.close();
    }

    @Test
    public void testConnect() throws Exception {
        assertNotNull(awbConnection);
        assertNotNull(connection);
    }

    @Test
    public void testCreate() throws Exception {
        assertTrue(awbConnection.create(25863221, 0001, "TestName", "TestTower", "TestUnit", "TestStatus", "TestSimple info"));

        // No duplicate card allowed
        assertFalse(awbConnection.create(25863221, 0001, "TestName", "TestTower", "TestUnit", "TestStatus", "TestSimple info"));
    }

    @Test
    public void testUpdate() throws Exception {
        assertTrue(awbConnection.update(25863221, "TestName2", "TestNameTower2", "TestUnit2", "TestStatus"));

        // Entry not found
        assertFalse(awbConnection.update(25863999, "TestName2", "TestNameTower2", "TestUnit2", "TestStatus"));

        // Delete created row
        awbConnection.delete(25863221);
    }

    @Test
    public void testMember() throws Exception {
        List<String> member = new ArrayList<String>();
        member.add("25863221");
        member.add("Name");
        member.add("Tower");
        member.add("Unit");
        member.add("Status");
        member.add("Info");

        assertTrue(awbConnection.member(25863221).size() == member.size());

        // Entry not found
        assertFalse(awbConnection.member(2586388).size() == member.size());

    }

    @Test
    public void testMezzaMember() throws Exception {
        List<String> member = new ArrayList<String>();
        member.add("25863221");
        member.add("Name");
        member.add("Tower");
        member.add("Unit");
        member.add("Status");
        member.add("Info");

        assertTrue(awbConnection.mezzaMember(0001).size() == member.size());

        // Mezza ID not found
        assertFalse(awbConnection.mezzaMember(999).size() == member.size());
    }

    @Test
    public void testLog() throws Exception {
        assertTrue(awbConnection.log(25863221));
    }

    @Test
    public void testDelete() throws Exception {
        awbConnection.create(25863222, 0002, "TestName", "TestTower", "TestUnit", "TestStatus", "TestSimple info");
        assertTrue(awbConnection.delete(25863222));

        // Entry not found
        assertFalse(awbConnection.delete(99999));
    }

    @Test
    public void testRowCount(){
        awbConnection.create(23, 0002, "TestName", "TestTower", "TestUnit", "TestStatus", "TestSimple info");
        awbConnection.create(3434, 0002, "TestName", "TestTower", "TestUnit", "TestStatus", "TestSimple info");
        awbConnection.create(34332, 0002, "TestName", "TestTower", "TestUnit", "TestStatus", "TestSimple info");

        assertEquals(3, awbConnection.rowCount());
        assertNotEquals(0, awbConnection.rowCount());

        awbConnection.delete(23);
        awbConnection.delete(3434);
        awbConnection.delete(34332);
    }
}