package net.sf.jodconverter.office;

import static org.testng.Assert.*;

import java.io.File;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

import net.sf.jodconverter.test.TestUtils;
import net.sf.jodconverter.util.ReflectionUtils;
import org.testng.annotations.Test;

@Test(groups="integration")
public class ManagedProcessOfficeManagerTest {

    private static final File OFFICE_HOME = TestUtils.getOfficeHome();
    private static final File OFFICE_PROFILE = TestUtils.getOfficeProfile();
    private static final String CONNECT_STRING = "socket,host=127.0.0.1,port=8100";
    private static final long RESTART_WAIT_TIME = 2 * 1000;

    public void executeTask() throws Exception {
        ManagedProcessOfficeManager officeManager = new ManagedProcessOfficeManager(OFFICE_HOME, OFFICE_PROFILE, CONNECT_STRING);
        ManagedOfficeProcess managedOfficeProcess = (ManagedOfficeProcess) ReflectionUtils.getPrivateField(officeManager, "managedOfficeProcess");
        OfficeProcess process = (OfficeProcess) ReflectionUtils.getPrivateField(managedOfficeProcess, "process");
        OfficeConnection connection = (OfficeConnection) ReflectionUtils.getPrivateField(managedOfficeProcess, "connection");
        
        officeManager.start();
        assertTrue(process.isRunning());
        assertTrue(connection.isConnected());
        
        MockOfficeTask task = new MockOfficeTask();
        officeManager.execute(task);
        assertTrue(task.isCompleted());
        
        officeManager.stop();
        assertFalse(connection.isConnected());
        assertFalse(process.isRunning());
        assertEquals(process.getExitCode(0, 0), 0);
    }

    public void restartAfterCrash() throws Exception {
        final ManagedProcessOfficeManager officeManager = new ManagedProcessOfficeManager(OFFICE_HOME, OFFICE_PROFILE, CONNECT_STRING);
        ManagedOfficeProcess managedOfficeProcess = (ManagedOfficeProcess) ReflectionUtils.getPrivateField(officeManager, "managedOfficeProcess");
        OfficeProcess process = (OfficeProcess) ReflectionUtils.getPrivateField(managedOfficeProcess, "process");
        OfficeConnection connection = (OfficeConnection) ReflectionUtils.getPrivateField(managedOfficeProcess, "connection");
        assertNotNull(connection);
        
        officeManager.start();
        assertTrue(process.isRunning());
        assertTrue(connection.isConnected());
        
        new Thread() {
            public void run() {
                MockOfficeTask badTask = new MockOfficeTask(10 * 1000);
                try {
                    officeManager.execute(badTask);
                    fail("task should be cancelled");
                    //FIXME being in a separate thread the test won't actually fail
                } catch (OfficeException officeException) {
                    assertTrue(officeException.getCause() instanceof CancellationException);
                }
            }
        }.start();
        Thread.sleep(500);
        Process underlyingProcess = (Process) ReflectionUtils.getPrivateField(process, "process");
        assertNotNull(underlyingProcess);
        underlyingProcess.destroy();  // simulate crash

        Thread.sleep(RESTART_WAIT_TIME);
        assertTrue(process.isRunning());
        assertTrue(connection.isConnected());

        MockOfficeTask goodTask = new MockOfficeTask();
        officeManager.execute(goodTask);
        assertTrue(goodTask.isCompleted());

        officeManager.stop();
        assertFalse(connection.isConnected());
        assertFalse(process.isRunning());
        assertEquals(process.getExitCode(0, 0), 0);
    }

    public void restartAfterTaskTimeout() throws Exception {
        final ManagedProcessOfficeManager officeManager = new ManagedProcessOfficeManager(OFFICE_HOME, OFFICE_PROFILE, CONNECT_STRING);
        officeManager.setTaskExecutionTimeout(1500);
        
        ManagedOfficeProcess managedOfficeProcess = (ManagedOfficeProcess) ReflectionUtils.getPrivateField(officeManager, "managedOfficeProcess");
        OfficeProcess process = (OfficeProcess) ReflectionUtils.getPrivateField(managedOfficeProcess, "process");
        OfficeConnection connection = (OfficeConnection) ReflectionUtils.getPrivateField(managedOfficeProcess, "connection");
        assertNotNull(connection);
        
        officeManager.start();
        assertTrue(process.isRunning());
        assertTrue(connection.isConnected());
        
        MockOfficeTask longTask = new MockOfficeTask(2000);
        try {
            officeManager.execute(longTask);
            fail("task should be timed out");
        } catch (OfficeException officeException) {
            assertTrue(officeException.getCause() instanceof TimeoutException);
        }

        Thread.sleep(RESTART_WAIT_TIME);
        assertTrue(process.isRunning());
        assertTrue(connection.isConnected());

        MockOfficeTask goodTask = new MockOfficeTask();
        officeManager.execute(goodTask);
        assertTrue(goodTask.isCompleted());

        officeManager.stop();
        assertFalse(connection.isConnected());
        assertFalse(process.isRunning());
        assertEquals(process.getExitCode(0, 0), 0);
    }

    public void restartWhenMaxTasksPerProcessReached() throws Exception {
        final ManagedProcessOfficeManager officeManager = new ManagedProcessOfficeManager(OFFICE_HOME, OFFICE_PROFILE, CONNECT_STRING);
        officeManager.setMaxTasksPerProcess(3);
        
        ManagedOfficeProcess managedOfficeProcess = (ManagedOfficeProcess) ReflectionUtils.getPrivateField(officeManager, "managedOfficeProcess");
        OfficeProcess process = (OfficeProcess) ReflectionUtils.getPrivateField(managedOfficeProcess, "process");
        OfficeConnection connection = (OfficeConnection) ReflectionUtils.getPrivateField(managedOfficeProcess, "connection");
        assertNotNull(connection);
        
        officeManager.start();
        assertTrue(process.isRunning());
        assertTrue(connection.isConnected());
        
        for (int i = 0; i < 3; i++) {
            MockOfficeTask task = new MockOfficeTask();
            officeManager.execute(task);
            assertTrue(task.isCompleted());
            int taskCount = (Integer) ReflectionUtils.getPrivateField(officeManager, "taskCount");
            assertEquals(taskCount, i + 1);
        }

        MockOfficeTask task = new MockOfficeTask();
        officeManager.execute(task);
        assertTrue(task.isCompleted());
        int taskCount = (Integer) ReflectionUtils.getPrivateField(officeManager, "taskCount");
        assertEquals(taskCount, 0);  //FIXME should be 1 to be precise

        officeManager.stop();
        assertFalse(connection.isConnected());
        assertFalse(process.isRunning());
        assertEquals(process.getExitCode(0, 0), 0);
    }

}
