/*
 * Copyright 2008 Alin Dreghiciu.
 * Copyright 2008 Peter Kriens.
 *
 * Licensed  under the  Apache License,  Version 2.0  (the "License");
 * you may not use  this file  except in  compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed  under the  License is distributed on an "AS IS" BASIS,
 * WITHOUT  WARRANTIES OR CONDITIONS  OF ANY KIND, either  express  or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.swissbox.bnd;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ops4j.lang.NullArgumentException;
import org.ops4j.lang.Ops4jException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Constants;
import aQute.bnd.osgi.Jar;

/**
 * Wrapper over PeterK's bnd lib.
 *
 * @author Alin Dreghiciu
 * @since 0.1.0, January 14, 2008
 */
public class BndUtils
{

    /**
     * Logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger( BndUtils.class );

    /**
     * Regex pattern for matching instructions when specified in url.
     */
    private static final Pattern INSTRUCTIONS_PATTERN =
    	Pattern.compile("([a-zA-Z_0-9-]+)=([\\-!\"'()\\[\\]*+,.\\\\0-9A-Z_a-z%;:=/\\s]+)?"); //added [], whitespace \s and backslash
    
    private static final Pattern CAMELCASE_PATTERN = 
    	Pattern.compile( "/\\b([A-Z][a-z]*){2,}\\b/");

    /**
     * Utility class. Ment to be used using static methods
     */
    private BndUtils()
    {
        // utility class
    }

    /**
     * Processes the input jar and generates the necessary OSGi headers using specified instructions.
     *
     * @param jarInputStream input stream for the jar to be processed. Cannot be null.
     * @param instructions   bnd specific processing instructions. Cannot be null.
     * @param jarInfo        information about the jar to be processed. Usually the jar url. Cannot be null or empty.
     *
     * @return an input stream for the generated bundle
     *
     * @throws NullArgumentException if any of the parameters is null
     * @throws IOException           re-thron during jar processing
     */
    public static InputStream createBundle( final InputStream jarInputStream,
                                            final Properties instructions,
                                            final String jarInfo )
        throws IOException
    {
        return createBundle( jarInputStream, instructions, jarInfo, OverwriteMode.KEEP );
    }

    /**
     * Processes the input jar and generates the necessary OSGi headers using specified instructions.
     *
     * @param jarInputStream input stream for the jar to be processed. Cannot be null.
     * @param instructions   bnd specific processing instructions. Cannot be null.
     * @param jarInfo        information about the jar to be processed. Usually the jar url. Cannot be null or empty.
     * @param overwriteMode  manifets overwrite mode
     *
     * @return an input stream for the generated bundle
     *
     * @throws NullArgumentException if any of the parameters is null
     * @throws IOException           re-thron during jar processing
     */
    public static InputStream createBundle( final InputStream jarInputStream,
                                            final Properties instructions,
                                            final String jarInfo,
                                            final OverwriteMode overwriteMode )
        throws IOException
    {
        NullArgumentException.validateNotNull( jarInputStream, "Jar URL" );
        NullArgumentException.validateNotNull( instructions, "Instructions" );
        NullArgumentException.validateNotEmpty( jarInfo, "Jar info" );

        LOG.debug( "Creating bundle for [" + jarInfo + "]" );
        LOG.debug( "Overwrite mode: " + overwriteMode );
        LOG.trace( "Using instructions " + instructions );

        final Jar jar = new Jar( "dot", jarInputStream );
        Manifest manifest = null;
        try 
        {
            manifest = jar.getManifest();
        }
        catch ( Exception e )
        {
            jar.close();
            throw new Ops4jException( e );
        }
                

        // Make the jar a bundle if it is not already a bundle
        if( manifest == null
            || OverwriteMode.KEEP != overwriteMode
            || ( manifest.getMainAttributes().getValue( Analyzer.EXPORT_PACKAGE ) == null
                 && manifest.getMainAttributes().getValue( Analyzer.IMPORT_PACKAGE ) == null )
            )
        {
            // Do not use instructions as default for properties because it looks like BND uses the props
            // via some other means then getProperty() and so the instructions will not be used at all
            // So, just copy instructions to properties
            final Properties properties = new Properties();
            properties.putAll( instructions );

            properties.put( "Generated-By-Ops4j-Pax-From", jarInfo );

            final Analyzer analyzer = new Analyzer();
            analyzer.setJar( jar );
            analyzer.setProperties( properties );
            if( manifest != null && OverwriteMode.MERGE == overwriteMode )
            {
                analyzer.mergeManifest( manifest );
            }
            checkMandatoryProperties( analyzer, jar, jarInfo );
            try
            {
                Manifest newManifest = analyzer.calcManifest();
                jar.setManifest( newManifest );
            }
            catch ( Exception e )
            {
                jar.close();
                throw new Ops4jException( e );
            }
        }

        return createInputStream( jar );
    }

    /**
     * Creates an piped input stream for the wrapped jar.
     * This is done in a thread so we can return quickly.
     *
     * @param jar the wrapped jar
     *
     * @return an input stream for the wrapped jar
     *
     * @throws java.io.IOException re-thrown
     */
    private static PipedInputStream createInputStream( final Jar jar )
        throws IOException
    {
        final CloseAwarePipedInputStream pin = new CloseAwarePipedInputStream();
        final PipedOutputStream pout = new PipedOutputStream( pin );

        new Thread()
        {
            public void run()
            {
                try
                {
                    jar.write( pout );
                }
                catch( Exception e )
                {
                    if (pin.closed)
                    {
                        // logging the message at DEBUG logging instead
                        // -- reading thread probably stopped reading
                        LOG.debug( "Bundle cannot be generated, pipe closed by reader", e );
                    }
                    else {
                        LOG.warn( "Bundle cannot be generated", e );
                    }
                }
                finally
                {
                    try
                    {
                        jar.close();
                        pout.close();
                    }
                    catch( IOException ignore )
                    {
                        // if we get here something is very wrong
                        LOG.error( "Bundle cannot be generated", ignore );
                    }
                }
            }
        }.start();

        return pin;
    }

    /**
     * Check if manadatory properties are present, otherwise generate default.
     *
     * @param analyzer     bnd analyzer
     * @param jar          bnd jar
     * @param symbolicName bundle symbolic name
     */
    private static void checkMandatoryProperties( final Analyzer analyzer,
                                                  final Jar jar,
                                                  final String symbolicName )
    {
        final String importPackage = analyzer.getProperty( Analyzer.IMPORT_PACKAGE );
        if( importPackage == null || importPackage.trim().length() == 0 )
        {
            analyzer.setProperty( Analyzer.IMPORT_PACKAGE, "*;resolution:=optional" );
        }
        final String exportPackage = analyzer.getProperty( Analyzer.EXPORT_PACKAGE );
        if( exportPackage == null || exportPackage.trim().length() == 0 )
        {
            analyzer.setProperty( Analyzer.EXPORT_PACKAGE, "*" );
        }
        final String localSymbolicName = analyzer.getProperty( Analyzer.BUNDLE_SYMBOLICNAME, symbolicName );
        analyzer.setProperty( Analyzer.BUNDLE_SYMBOLICNAME, generateSymbolicName( localSymbolicName ) );
    }

    /**
     * Processes symbolic name and replaces osgi spec invalid characters with "_".
     *
     * @param symbolicName bundle symbolic name
     *
     * @return a valid symbolic name
     */
    private static String generateSymbolicName( final String symbolicName )
    {
        return symbolicName.replaceAll( "[^a-zA-Z_0-9.-]", "_" );
    }

    /**
     * Parses bnd instructions out of an url query string.
     *
     * @param query query part of an url.
     *
     * @return parsed instructions as properties
     *
     * @throws java.net.MalformedURLException if provided path does not comply to syntax.
     */
    public static Properties parseInstructions( final String query )
        throws MalformedURLException
    {
        final Properties instructions = new Properties();
        if( query != null )
        {
            try
            {
                // just ignore for the moment and try out if we have valid properties separated by "&"
                final String segments[] = query.split( "&" );
                for( String segment : segments )
                {
                    // do not parse empty strings
                    if( segment.trim().length() > 0 )
                    {
                        final Matcher matcher = INSTRUCTIONS_PATTERN.matcher( segment );
                        if( matcher.matches() )
                        {
                            String key = matcher.group( 1 );
                            String val = matcher.group( 2 );
                            instructions.setProperty(
                                verifyKey(key),
                                val != null ? URLDecoder.decode( val, "UTF-8" ) : ""
                            );
                        }
                        else
                        {
                            throw new MalformedURLException( "Invalid syntax for instruction [" + segment
                                                             + "]. Take a look at http://www.aqute.biz/Code/Bnd."
                            );
                        }
                    }
                }
            }
            catch( UnsupportedEncodingException e )
            {
                // thrown by URLDecoder but it should never happen
                throwAsMalformedURLException( "Could not retrieve the instructions from [" + query + "]", e );
            }
        }
        return instructions;
    }

    private static String verifyKey(String key) {
		List<String> list = new ArrayList<String>(Constants.headers);
		//patch the header list for an additional Web-ContextPath
		list.add("Web-ContextPath");
		if (list.contains(key)) {
			return key;
    	} else {
			//this is not a key contained in the headers list
			//either a specialized Camel-Case
			//which is forwarded
			if (CAMELCASE_PATTERN.matcher(key).matches()) { 
				return key;
    		} else {
				//no Camel Case check if it exists in list 
    			for (String header : list) {
					if (header.equalsIgnoreCase(key)) {
						return header;
					}
				}
    			return key;
			}
		}
	}

	/**
     * Creates an MalformedURLException with a message and a cause.
     *
     * @param message exception message
     * @param cause   exception cause
     *
     * @throws MalformedURLException the created MalformedURLException
     */
    private static void throwAsMalformedURLException( final String message, final Exception cause )
        throws MalformedURLException
    {
        final MalformedURLException exception = new MalformedURLException( message );
        exception.initCause( cause );
        throw exception;
    }

    /**
     * PipedInputStream implementation that keeps track of whether it has been closed or not.
     */
    private static final class CloseAwarePipedInputStream extends PipedInputStream
    {
        private boolean closed = false;

        public void close() throws IOException
        {
            closed = true;
            super.close();
        }
    }
}
