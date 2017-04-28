/*
 * Copyright 2008 Alin Dreghiciu.
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
package org.ops4j.pax.swissbox.extender;

import java.util.Map;

/**
 * Manifest entries filter.
 *
 * @author Alin Dreghiciu
 * @since 0.2.0, February 09, 2008
 */
public interface ManifestFilter
{

    /**
     * Returns a list of manifest entries matching the filter.
     *
     * @param entries entries to be filtered
     *
     * @return matching entries
     */
    Map<String, String> match( Map<String, String> entries );

}
