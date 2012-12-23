/*
 * Copyright 2011 Harald Wellmann.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.swissbox.framework;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.ops4j.pax.swissbox.tracker.ServiceLookup;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.service.startlevel.StartLevel;

/**
 * Implements the {@link RemoteFramework} interface by instantiating a local {@link Framework},
 * exporting it via an RMI registry and delegating all remote calls to the local framework.
 * 
 * @author Harald Wellmann
 */
public class RemoteFrameworkImpl implements RemoteFramework
{
    private static Logger LOG = Logger.getLogger( RemoteFrameworkImpl.class.getName() );

    private Framework framework;
    private Registry registry;
    private String name;

    public RemoteFrameworkImpl( Map<String, String> frameworkProperties ) throws RemoteException,
        AlreadyBoundException, BundleException
    {
        FrameworkFactory frameworkFactory = findFrameworkFactory();
        this.framework = frameworkFactory.newFramework( frameworkProperties );

        export();
    }

    private void export() throws RemoteException, AccessException
    {
        String port = System.getProperty( RMI_PORT_KEY, "1099" );
        name = System.getProperty( RMI_NAME_KEY );
        registry = LocateRegistry.getRegistry( Integer.parseInt( port ) );
        URL location1 = getClass().getProtectionDomain().getCodeSource().getLocation();
        URL location2 = Bundle.class.getProtectionDomain().getCodeSource().getLocation();
        URL location3 = ServiceLookup.class.getProtectionDomain().getCodeSource().getLocation();
        System.setProperty( "java.rmi.server.codebase", location1 + " " + location2 + " " + location3);
        Remote remote = UnicastRemoteObject.exportObject( this, 0 );
        registry.rebind( name, remote );
    }

    public void init() throws RemoteException, BundleException
    {
        framework.init();
    }

    public void start() throws RemoteException, BundleException
    {
        framework.start();
    }

    public void stop() throws RemoteException, BundleException
    {
        framework.stop();
        try
        {
            registry.unbind( name );
        }
        catch ( NotBoundException exc )
        {
            throw new IllegalStateException( exc );
        }
        UnicastRemoteObject.unexportObject( this, true );
    }

    public long installBundle( String bundleUrl ) throws RemoteException, BundleException
    {
        Bundle bundle = framework.getBundleContext().installBundle( bundleUrl );
        return bundle.getBundleId();
    }

    public long installBundle( String bundleLocation, byte[] bundleData ) throws RemoteException,
        BundleException
    {
        Bundle bundle =
            framework.getBundleContext().installBundle( bundleLocation,
                new ByteArrayInputStream( bundleData ) );
        return bundle.getBundleId();
    }

    public void startBundle( long bundleId ) throws RemoteException, BundleException
    {
        framework.getBundleContext().getBundle( bundleId ).start();
    }

    public void stopBundle( long bundleId ) throws RemoteException, BundleException
    {
        framework.getBundleContext().getBundle( bundleId ).stop();
    }

    public void setBundleStartLevel( long bundleId, int startLevel ) throws RemoteException,
        BundleException
    {
        BundleContext bc = framework.getBundleContext();
        StartLevel sl = ServiceLookup.getService( bc, StartLevel.class );
        Bundle bundle = bc.getBundle( bundleId );
        sl.setBundleStartLevel( bundle, startLevel );
    }

    public void uninstallBundle( long id ) throws RemoteException, BundleException
    {
        framework.getBundleContext().getBundle( id ).uninstall();
    }

    public FrameworkFactory findFrameworkFactory()
    {
        ServiceLoader<FrameworkFactory> loader = ServiceLoader.load( FrameworkFactory.class );
        FrameworkFactory factory = loader.iterator().next();
        return factory;
    }

    private static Map<String, String> buildFrameworkProperties( String[] args )
    {        
        Map<String, String> props = new HashMap<String, String>();
        for (String arg : args) {
            if (arg.startsWith("-F")) {
                int eq = arg.indexOf("=");
                if (eq == -1) {
                    String key = arg.substring( 2 );
                    props.put( key, null );
                }
                else {
                    String key = arg.substring( 2, eq );
                    String value = arg.substring ( eq+1 );
                    props.put( key, value );                    
                }
            }
            else {
                LOG.warning( "ignoring unknown argument " + arg );
            }
        }
        return props;
    }

    public void callService( String filter, String methodName ) throws RemoteException,
        BundleException
    {
        try
        {
            LOG.fine( "acquiring service " + filter );
            BundleContext bc = framework.getBundleContext();
            Object service = ServiceLookup.getServiceByFilter( bc, filter );
            Class<? extends Object> klass = service.getClass();
            Method method;
            try
            {
                method = klass.getMethod( methodName, Object[].class );
                LOG.fine( "calling service method " + method );
                method.invoke( service, (Object) new Object[]{} );
            }
            catch ( NoSuchMethodException e )
            {
                method = klass.getMethod( methodName );
                LOG.fine( "calling service method  " + method );
                method.invoke( service );
            }
        }
        catch ( SecurityException exc )
        {
            throw new IllegalStateException( exc );
        }
        catch ( NoSuchMethodException exc )
        {
            throw new IllegalStateException( exc );
        }
        catch ( IllegalArgumentException exc )
        {
            throw new IllegalStateException( exc );
        }
        catch ( IllegalAccessException exc )
        {
            throw new IllegalStateException( exc );
        }
        catch ( InvocationTargetException exc )
        {
            throw new IllegalStateException( exc );
        }
    }

    public void setFrameworkStartLevel( int startLevel )
    {
        BundleContext bc = framework.getBundleContext();
        StartLevel sl = ServiceLookup.getService( bc, StartLevel.class );
        sl.setStartLevel( startLevel );
    }

    public boolean setFrameworkStartLevel( final int startLevel, long timeout )
        throws RemoteException
    {
        BundleContext bc = framework.getBundleContext();
        final StartLevel sl = ServiceLookup.getService( bc, StartLevel.class );
        final CountDownLatch latch = new CountDownLatch( 1 );
        bc.addFrameworkListener( new FrameworkListener()
        {

            public void frameworkEvent( FrameworkEvent frameworkEvent )
            {
                switch( frameworkEvent.getType() )
                {
                    case FrameworkEvent.STARTLEVEL_CHANGED:
                        if( sl.getStartLevel() == startLevel )
                        {
                            latch.countDown();
                        }
                }
            }
        } );
        sl.setStartLevel( startLevel );
        boolean startLevelReached;
        try
        {
            startLevelReached = latch.await( timeout, TimeUnit.MILLISECONDS );
            return startLevelReached;
        }
        catch ( InterruptedException exc )
        {
            throw new RemoteException( "interrupted while waiting", exc );
        }
    }

    public void waitForState( long bundleId, int state, long timeoutInMillis )
        throws RemoteException, BundleException
    {
        throw new UnsupportedOperationException( "not yet implemented" );
    }

    public static void main( String[] args ) throws RemoteException, AlreadyBoundException,
        BundleException, InterruptedException
    {
        LOG.fine( "starting RemoteFrameworkImpl" );
        Map<String, String> props = buildFrameworkProperties( args );
        RemoteFrameworkImpl impl = new RemoteFrameworkImpl( props );
        impl.start();
    }

    public int getBundleState( long bundleId ) throws RemoteException, BundleException
    {
        Bundle bundle = framework.getBundleContext().getBundle( bundleId );
        if( bundle == null )
        {
            throw new BundleException( String.format( "bundle [%d] does not exist", bundleId ) );
        }
        return bundle.getState();
    }
}
