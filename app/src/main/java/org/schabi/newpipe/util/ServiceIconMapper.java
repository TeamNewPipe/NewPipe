package org.schabi.newpipe.util;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;

/**
 * Created by Chrsitian Schabesberger on 09.10.17.
 * ServiceIconMapper.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class ServiceIconMapper {
    public static int getIconResource(int service_id) {
        switch(service_id) {
            case 0:
                return R.drawable.youtube;
            case 1:
                return R.drawable.soud_cloud;
            default:
                return R.drawable.service;
        }
    }
}
