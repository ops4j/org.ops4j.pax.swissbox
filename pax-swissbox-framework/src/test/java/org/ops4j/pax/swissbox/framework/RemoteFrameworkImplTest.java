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

import java.io.File;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ops4j.exec.DefaultJavaRunner;
import org.ops4j.pax.swissbox.tracker.ServiceLookup;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.FrameworkFactory;

public class RemoteFrameworkImplTest
{
    private DefaultJavaRunner javaRunner;
    private FrameworkFactory frameworkFactory;

    @Before
    public void setUp() throws RemoteException
    {
        LocateRegistry.createRegistry( 1099 );
        javaRunner = new DefaultJavaRunner( false );
        frameworkFactory = FrameworkFactoryFinder.loadSingleFrameworkFactory();
        File storage = new File("target", "storage");
        String[] vmOptions = new String[]{
            "-Dosgi.console=6666",
            "-Dosgi.clean=true",
            "-Dorg.osgi.framework.storage=" + storage.getPath(),
            "-Dpax.swissbox.framework.rmi.port=1099",
            "-Dpax.swissbox.framework.rmi.name=PaxRemoteFramework"
        };
        javaRunner.exec( vmOptions, buildClasspath(), RemoteFrameworkImpl.class.getName(),
            null, findJavaHome(), null );
    }

    @After
    public void tearDown() throws InterruptedException
    {
        if( javaRunner != null )
        {
            javaRunner.shutdown();
        }
    }

    @Test
    public void forkEquinox() throws BundleException, IOException, InterruptedException,
        NotBoundException
    {
        RemoteFramework framework = findRemoteFramework (1099, "PaxRemoteFramework");
        framework.start();

        long commonsIoId = framework.installBundle( "file:target/bundles/commons-io-2.1.jar" );
        framework.startBundle( commonsIoId );

        framework.stop();
    }
    
    private RemoteFramework findRemoteFramework(int port, String rmiName )
    {
        RemoteFramework framework = null;
        long startedTrying = System.currentTimeMillis();

            do
            {
                try
                {
                    Registry reg = LocateRegistry.getRegistry(  );
                    framework = (RemoteFramework) reg.lookup( rmiName );
                }
                catch ( Exception e )
                {
                    // ignore 
                }
            }
            while ( framework == null && ( System.currentTimeMillis() < startedTrying + 10000 ) );
        return framework;
    }
        
    private String[] buildClasspath()
    {
        String frameworkPath = toPath( frameworkFactory.getClass() );
        String launcherPath = toPath( RemoteFrameworkImpl.class );
        String serviceLookupPath = toPath( ServiceLookup.class );
        return new String[]{ frameworkPath, launcherPath, serviceLookupPath };
    }

    private static String toPath( Class<?> klass )
    {
        return klass.getProtectionDomain().getCodeSource().getLocation().getPath();
    }

    private String findJavaHome()
    {
        String javaHome = System.getenv( "JAVA_HOME" );
        if( javaHome == null )
        {
            javaHome = System.getProperty( "java.home" );
        }
        return javaHome;
    }
}
