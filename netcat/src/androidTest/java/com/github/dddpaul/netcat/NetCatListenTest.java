package com.github.dddpaul.netcat;

import android.util.Log;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.github.dddpaul.netcat.NetCater.Op.DISCONNECT;
import static com.github.dddpaul.netcat.NetCater.Op.LISTEN;
import static com.github.dddpaul.netcat.NetCater.Op.RECEIVE;
import static com.github.dddpaul.netcat.NetCater.Op.SEND;
import static com.github.dddpaul.netcat.NetCater.Result;
import static org.hamcrest.core.Is.is;

@Config( emulateSdk = 18 )
@RunWith( RobolectricTestRunner.class )
public class NetCatListenTest extends Assert implements NetCatListener
{
    final static String INPUT_TEST = "Input from this test";
    final static String INPUT_NC = "Input from netcat process";
    final static String HOST = "localhost";
    final static String PORT = "9998";
    final String CLASS_NAME = ( (Object) this ).getClass().getSimpleName();

    static List<String> nc = new ArrayList<>();

    NetCater netCat;
    Result result;
    CountDownLatch latch;
    Process process;

    /**
     * Require netcat-openbsd package for Debian/Ubuntu
     */
    @BeforeClass
    public static void init()
    {
        nc.add( "nc" );
        nc.add( "-v" );
        nc.add( HOST );
        nc.add( PORT );
    }

    @Before
    public void setUp() throws Exception
    {
        ShadowLog.stream = System.out;
        netCat = new NetCat( this );
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

    @Test
    public void test() throws InterruptedException, IOException
    {
        new Thread( new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    Thread.sleep( 500 );
                    process = new ProcessBuilder( nc ).redirectErrorStream( true ).start();
                } catch( Exception e ) {
                    e.printStackTrace();
                }
            }
        }).start();

        Socket socket = listen();
        netCat.setSocket( socket );

        // Send string to nc process
        netCat.setInput( new ByteArrayInputStream( INPUT_TEST.getBytes() ));
        netCat.execute( SEND.toString() );
        latch.await( 5, TimeUnit.SECONDS );

        assertNotNull( result );
        assertEquals( SEND, result.op );

        // Get received string by nc process
        BufferedReader b = new BufferedReader( new InputStreamReader( process.getInputStream() ));
        String line;
        do {
            line = b.readLine();
            Log.i( CLASS_NAME, line  );

        } while( !INPUT_TEST.equals( line ));

        // Send string from nc process
        process.getOutputStream().write( INPUT_NC.getBytes() );
        process.getOutputStream().flush();
        new Thread( new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    Thread.sleep( 500 );
                } catch( Exception e ) {
                    e.printStackTrace();
                }
                process.destroy();
            }
        }).start();

        // Prepare to receive string from nc process
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        netCat.setOutput( output );
        netCat.execute( RECEIVE.toString() );
        latch.await( 5, TimeUnit.SECONDS );

        assertNotNull( result );
        assertThat( result.op, is( RECEIVE ));
        line = new String( output.toByteArray() ).trim();
        Log.i( CLASS_NAME, line  );
        assertThat( line, is( INPUT_NC ));

        disconnect();
    }

    public Socket listen() throws InterruptedException
    {
        netCat.execute( LISTEN.toString(), PORT );
        latch.await( 5, TimeUnit.SECONDS );

        assertNotNull( result );
        assertNull( result.exception );
        assertThat( result.op, is( LISTEN ));
        assertNotNull( result.getSocket() );
        return result.getSocket();
    }

    public void disconnect() throws InterruptedException
    {
        netCat.execute( DISCONNECT.toString() );
        latch.await( 5, TimeUnit.SECONDS );

        assertNotNull( result );
        assertNull( result.exception );
        assertThat( result.op, is( DISCONNECT ));
    }
}