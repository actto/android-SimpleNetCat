/**
 * Require nc binary (netcat-openbsd package for Debian/Ubuntu).
 */
package com.github.dddpaul.netcat;

import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static com.github.dddpaul.netcat.NetCater.Proto;
import static com.github.dddpaul.netcat.NetCater.Result;

@Config( emulateSdk = 18 )
@RunWith( RobolectricTestRunner.class )
public class UdpNetCatTest extends NetCatTestParent implements NetCatListener
{
    @Before
    public void setUp() throws Exception
    {
        netCat = new UdpNetCat( this );
        inputFromTest = INPUT_TEST + "\n";
        inputFromProcess = INPUT_NC +"\n";
    }

    @After
    public void tearDown() throws InterruptedException
    {
        disconnect();
        process.destroy();
    }

    @Test
    public void testUdpConnect() throws IOException, InterruptedException
    {
        int port = 9998;
        List<String> listener = prepareNetCatProcess( Proto.UDP, true, port );
        process = new ProcessBuilder( listener ).redirectErrorStream( true ).start();

        // Execute connect operation after some delay
        Thread.sleep( 500 );
        connect( port );

        send();
        receive();
    }

    @Test
    public void testUdpListen() throws IOException, InterruptedException
    {
        int port = 9997;

        // Connect to NetCat by external nc after some delay required for NetCat listener to start
        final List<String> dialer = prepareNetCatProcess( Proto.UDP, false, port );
        new Thread( new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    Thread.sleep( 500 );
                    process = new ProcessBuilder( dialer ).redirectErrorStream( true ).start();
                } catch( Exception e ) {
                    e.printStackTrace();
                }
            }
        } ).start();

        // Start NetCat listener
        listen( port );

        // Stop receiving after some delay required for NetCat to receive test data
        new Thread( new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    Thread.sleep( 1000 );
                    netCat.cancel();
                } catch( Exception e ) {
                    e.printStackTrace();
                }
            }
        } ).start();

        receive();
        send();
    }

    @Override
    public void netCatIsStarted()
    {
        latch = new CountDownLatch( 1 );
    }

    @Override
    public void netCatIsCompleted( Result result )
    {
        this.result = result;
        latch.countDown();
    }

    @Override
    public void netCatIsFailed( Result result )
    {
        this.result = result;
        Log.e( CLASS_NAME, result.getErrorMessage() );
        latch.countDown();
    }
}
