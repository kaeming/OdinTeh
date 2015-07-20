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

/* Shinsoo
	Cygnus Knight Creator
	Ereve
*/

var status = 0;
var job;

importPackage(net.sf.odinms.client);

function start() {
	status = -1;
	action(1, 0, 0);
}

function action(mode, type, selection) {
	if (mode == -1) {
		cm.dispose();
	} else {
		if (mode == 0) {
			cm.sendOk("If you are intent on protecting the Maple World on your own, then there's nothing I can say that will convince you otherwise...");
			cm.dispose();
			return;
		}
		if (mode == 1) {
			status++;
		} else {
			status--;
		}
		if (status == 0) {
			if (cm.getJob().isA(MapleJob.NOBLESSE)) {
				cm.getItemEffect(2022458).applyTo(cm.getPlayer());
				cm.sendOk("Don't stop training. Every ounce of your energy is required to protect Maple World...");             
				cm.dispose();
				return;
			} else {
				if (cm.getPlayer().getChildId() > 0) {
					cm.dispose();
					return;
				} else {
					if (cm.getPlayer().getLevel() >= 20) {
						cm.sendYesNo("Are you ready to become a Cygnus Knight to serve and protect the Maple World? If so, then let's begin.");
					} else {
						cm.sendOk("The Cygnus Knights are looking for someone with more experience.");
						cm.dispose();
						return;
					}
				}
			}
		} else if (status == 1) {
			cm.sendCygnusCreation();
			cm.dispose();
			return;
		}
	}
}