/*
 * Copyright (C) 2012 Dr. John Lindsay <jlindsay@uoguelph.ca>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package whitebox.geospatialfiles.shapefile;

/**
 *
 * @author Dr. John Lindsay <jlindsay@uoguelph.ca>
 */
public enum ShapeType {
    NULLSHAPE, POINT, UNUSED1, POLYLINE, UNUSED2, POLYGON, UNUSED3, UNUSED4,
    MULTIPOINT, UNUSED5, UNUSED6, POINTZ, UNUSED7, POLYLINEZ, UNUSED8, POLYGONZ,
    UNUSED9, UNUSED10, MULTIPOINTZ, UNUSED11, UNUSED12, POINTM, UNUSED13, POLYLINEM,
    UNUSED14, POLYGONM, UNUSED15, UNUSED16, MULTIPOINTM, UNUSED17, UNUSED18, MULTIPATCH;
    
    public ShapeType getBaseType() {
        switch (this) {
            case POINT:
            case POINTZ:
            case POINTM:
                return POINT;
            
            case MULTIPOINT:
            case MULTIPOINTZ:
            case MULTIPOINTM:
                return MULTIPOINT;
                
            case POLYLINE:
            case POLYLINEZ:
            case POLYLINEM:
                return POLYLINE;
                
            case POLYGON:
            case POLYGONZ:
            case POLYGONM:
                return POLYGON;
            default:
                return this;
        }
    }
}
