/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc> 
                       Matthias Butz <matze@odinms.de>
                       Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License version 3
    as published by the Free Software Foundation. You may not use, modify
    or distribute this program under any other version of the
    GNU Affero General Public License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package odinms.client;

import odinms.net.LongValueHolder;

public enum MapleDisease implements LongValueHolder {
	NULL(0x0),
	SLOW(0x1),			// 01 00 00 00 00 00 00 00 0x1
	SEDUCE(0x80),
	STUN(0x2000000000000L),		// 00 00 00 00 00 00 02 00 0x2000000000000L
	POISON(0x4000000000000L),	// 00 00 00 00 00 00 04 00 0x4000000000000L
	SEAL(0x8000000000000L),		// 00 00 00 00 00 00 08 00 0x8000000000000L
	DARKNESS(0x10000000000000L),	// 00 00 00 00 00 00 10 00 0x10000000000000L
	WEAKEN(0x4000000000000000L)	// 00 00 00 00 00 00 00 40 0x4000000000000000L
	;
	
	private long i;

	private MapleDisease(long i) {
		this.i = i;
	}

	@Override
	public long getValue() {
		return i;
	}
}
