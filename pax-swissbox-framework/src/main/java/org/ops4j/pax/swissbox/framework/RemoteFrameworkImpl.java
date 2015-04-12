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
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.service.startlevel.StartLevel;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Implements the {@link RemoteFramework} interface by instantiating a local {@link Framework},
 * exporting it via an RMI registry and delegating all remote calls to the local framework.
 * 
 * @author Harald Wellmann
 */
public class RemoteFrameworkImpl implements RemoteFramework
{
    /*
     * The use of java.util.logging instead of SLF4J is intentional
     * to simplify classpath setup.
     */
    private static Logger LOG = Logger.getLogger( RemoteFrameworkImpl.class.getName() );

    private Framework framework;
    private Registry registry;
    private String name;
    private long timeout; 

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
        timeout = Long.parseLong( System.getProperty( TIMEOUT_KEY, "10000") );
        registry = LocateRegistry.getRegistry( Integer.parseInt( port ) );
        URL location1 = getClass().getProtectionDomain().getCodeSource().getLocation();
        URL location2 = Bundle.class.getProtectionDomain().getCodeSource().getLocation();
        URL location3 = ServiceLookup.class.getProtectionDomain().getCodeSource().getLocation();
        System.setProperty( "java.rmi.server.codebase", location1 + " " + location2 + " "
                + location3 );
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
            framework.waitForStop(timeout);
        }
        catch (InterruptedException exc) 
        {
            LOG.severe("framework did not stop within timeout");
        }
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

    public long installBundle( String bundleUrl, boolean start, int startLevel )
        throws RemoteException, BundleException
    {
        BundleContext bundleContext = framework.getBundleContext();
        Bundle bundle = bundleContext.installBundle( bundleUrl );
        setupBundle( start, startLevel, bundleContext, bundle );
        return bundle.getBundleId();
    }

    public long installBundle( String bundleLocation, byte[] bundleData, boolean start,
            int startLevel ) throws RemoteException, BundleException
    {
        BundleContext bundleContext = framework.getBundleContext();
        Bundle bundle =
            bundleContext.installBundle( bundleLocation, new ByteArrayInputStream( bundleData ) );
        setupBundle( start, startLevel, bundleContext, bundle );
        return bundle.getBundleId();
    }

    private static void setupBundle( boolean start, int startLevel, BundleContext bundleContext,
            Bundle bundle ) throws BundleException
    {
        StartLevel sl = ServiceLookup.getService( bundleContext, StartLevel.class );
        sl.setBundleStartLevel( bundle, startLevel );

        if( start )
        {
            bundle.start();
        }
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
        for( String arg : args )
        {
            if( arg.startsWith( "-F" ) )
            {
                int eq = arg.indexOf( "=" );
                if( eq == -1 )
                {
                    String key = arg.substring( 2 );
                    props.put( key, null );
                }
                else
                {
                    String key = arg.substring( 2, eq );
                    String value = arg.substring( eq + 1 );
                    props.put( key, value );
                }
            }
            else
            {
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

    public void setFrameworkStartLevel( int startLevel ) throws RemoteException
    {
        setFrameworkStartLevel( startLevel, 0 );
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

    public int getBundleState( long bundleId ) throws RemoteException, BundleException
    {
        Bundle bundle = framework.getBundleContext().getBundle( bundleId );
        if( bundle == null )
        {
            throw new BundleException( String.format( "bundle [%d] does not exist", bundleId ) );
        }
        return bundle.getState();
    }

    public RemoteServiceReference[] getServiceReferences( String filter ) throws RemoteException,
        BundleException, InvalidSyntaxException
    {
        return getServiceReferences( filter, -1, null );
    }

    public RemoteServiceReference[] getServiceReferences( String filter, long timeout,
            TimeUnit timeUnit ) throws RemoteException, BundleException,
        InvalidSyntaxException
    {
        BundleContext bundleContext = framework.getBundleContext();
        ServiceReference[] serviceReferences = bundleContext.getAllServiceReferences( null, filter );
        if( serviceReferences == null )
        {
            if( timeout < 0 )
            {
                return new RemoteServiceReference[0];
            }
            ServiceTracker tracker =
                new ServiceTracker( bundleContext, bundleContext.createFilter( filter ), null );
            tracker.open( true );
            try
            {
                tracker.waitForService( timeUnit.toMillis( timeout ) );
                serviceReferences = tracker.getServiceReferences();
                if( serviceReferences == null )
                {
                    throw new IllegalStateException( "services vanished too fast..." );
                }
            }
            catch ( InterruptedException e )
            {
                throw new RuntimeException( "interrupted!", e );
            }
            finally
            {
                tracker.close();
            }
        }
        RemoteServiceReference[] remoteRefs = new RemoteServiceReference[serviceReferences.length];
        for( int i = 0; i < remoteRefs.length; i++ )
        {
            ServiceReference reference = serviceReferences[i];
            final String serviceFilter =
                "(&(" + Constants.SERVICE_ID + "=" + reference.getProperty( Constants.SERVICE_ID )
                        + ")" + filter + ")";
            final String[] keys = reference.getPropertyKeys();
            final Map<String, Object> values = new HashMap<String, Object>();
            for( String key : keys )
            {
                values.put( key, reference.getProperty( key ) );
            }
            remoteRefs[i] = new RemoteServiceReferenceImpl( values, serviceFilter );
        }
        return remoteRefs;
    }

    public Object invokeMethodOnService( RemoteServiceReference reference, String methodName,
            Object... args ) throws RemoteException, Exception
    {
        Class<?>[] argTypes = new Class<?>[args.length];
        for( int i = 0; i < argTypes.length; i++ )
        {
            Object object = args[i];
            if( object == null )
            {
                throw new IllegalArgumentException(
                    "argument "
                            + i
                            + " is null, use invokeMethodOnService(RemoteServiceReference, String, Class[], Object[]) if you want to call a service with null argument values" );
            }
            argTypes[i] = object.getClass();
        }
        return invokeMethodOnService( reference, methodName, argTypes, args );
    }

    public Object invokeMethodOnService( RemoteServiceReference reference, String methodName,
            Class<?>[] parameterTypes, Object[] args ) throws RemoteException,
        Exception
    {
        BundleContext bundleContext = framework.getBundleContext();
        ServiceReference[] allServiceReferences =
            bundleContext.getAllServiceReferences( null, reference.getServiceFilter() );
        if( allServiceReferences == null || allServiceReferences.length == 0 )
        {
            throw new IllegalStateException( "service is no longer present" );
        }
        if( allServiceReferences.length > 1 )
        {
            throw new AssertionError(
                "more than one service is matching the reference, this should never happen" );
        }
        Object service = bundleContext.getService( allServiceReferences[0] );
        if( service == null )
        {
            throw new IllegalStateException( "service has vanished between calls" );
        }
        try
        {
            Method method = service.getClass().getMethod( methodName, parameterTypes );
            return method.invoke( service, args );
        }
        finally
        {
            bundleContext.ungetService( allServiceReferences[0] );
        }
    }

    public static void main( String[] args ) throws RemoteException, AlreadyBoundException,
        BundleException, InterruptedException
    {
        LOG.fine( "starting RemoteFrameworkImpl" );
        Map<String, String> props = buildFrameworkProperties( args );
        new RemoteFrameworkImpl( props );
    }
}
