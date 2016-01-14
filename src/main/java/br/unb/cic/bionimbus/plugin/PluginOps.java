/*
    BioNimbuZ is a federated cloud platform.
    Copyright (C) 2012-2015 Laboratory of Bioinformatics and Data (LaBiD), 
    Department of Computer Science, University of Brasilia, Brazil

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package br.unb.cic.bionimbus.plugin;

import org.codehaus.jackson.annotate.JsonSubTypes;

/**
 * Created by IntelliJ IDEA. User: edward Date: 5/24/12 Time: 4:26 PM To change
 * this template use File | Settings | File Templates.
 */
@JsonSubTypes({
    @JsonSubTypes.Type(value = PluginInfo.class, name = "info"),
    @JsonSubTypes.Type(value = PluginFile.class, name = "file")
})
public interface PluginOps {
}
