/*
This file is part of the OdinMS Maple Story Server
Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
Matthias Butz <matze@odinms.de>
Jan Christian Meyer <vimes@odinms.de>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation version 3 as published by
the Free Software Foundation. You may not use, modify or distribute
this program under any other version of the GNU Affero General Public
License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.server.handlers.channel;

import java.util.Random;

import tools.StringUtil;

import client.MapleCharacter;
import tools.MaplePacketCreator;
import tools.data.input.SeekableLittleEndianAccessor;
import client.MapleClient;
import client.command.Commands;

public final class GeneralchatHandler extends net.AbstractMaplePacketHandler {

    public final void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        String s = slea.readMapleAsciiString();
        MapleCharacter chr = c.getPlayer();
		Random r = new Random();
		String[] msgs = {
				"So how's the weather today?",
				"Why am I such a loser?",
				"I hate my life; cut myself every day...",
				"I love the GMs on this server! <3",
				"S>GFA Scroll 60% Offer!",
				"I hate you.",
				"cc plz",
				"cc pl0x",
				"What's the exp rate?",
				"Hold on, my mom's calling.",
				"brb",
				"why you ksing me?",
				"defame me plz",
				"Support KONY 2012!",
				"how i get to henesys??",
				"Looking for Mushroom Shrine party!",
				"Me gusta queso. <3",
				"Mi madre llama, espera.",
				"ahhh i see",
				"jajajajaja",
				"lol noob. i level 312 @_@",
				"looking for black girl any age hmu",
				"hey wait",
				"llama forever <3333",
				"bbw black girls <333333333",
				"any weight loss tips?",
				"h4x!!",
				"and yes, i use BT to bot",
				"exactly why you should",
				"mario party 9 is out!",
				"trade me",
				"pervert!",
				"watching everybody hates chris soooo funny",
				"I like pencils",
				"TRADE ME",
				"IT WORKED!@!@"
		};
		
		String[] swear = {
				"fuck", "shit", "bitch", "hell", "ass", "damn", "penis", "vagina", "genitals", "crap", "screw", "suck", "piss", "dick", "cock", "fluk", "fluck", "ffff", "shat", "billshut", "bullsh", "!!!", "fking", "furk", "fark",
				"f@ck", "sh@t", "b@tch", "h3ll", "@ss", "d@mn", "p3nis", "v@gina", "g3nitals", "cr@p", "scr3w", "sukk", "p!ss", "d!ck", "c@ck", "fuc", "fuk", "regina", "badmin"
		};
        char heading = s.charAt(0);
        if (heading == '/' || heading == '!' || heading == '@') {
            String[] sp = s.split(" ");
            sp[0] = sp[0].toLowerCase().substring(1);
            if (!Commands.executePlayerCommand(c, sp, heading)) {
                if (chr.isGM()) {
                    if (!Commands.executeGMCommand(c, sp, heading)) {
                        Commands.executeAdminCommand(c, sp, heading);
                    }
                }
            }
        } else { //just a regular message
            if (!chr.isHidden()) {
    			if (StringUtil.countCharacters(s, '%') > 4 || StringUtil.countCharacters(s, '@') > 1 ||
    					StringUtil.countCharacters(s, '+') > 1 || StringUtil.countCharacters(s, '$') > 1 ||
    					StringUtil.countCharacters(s, '&') > 6 || StringUtil.countCharacters(s, '~') > 1 ||
    					StringUtil.countCharacters(s, '#') > 6 || StringUtil.countCharacters(s, '*') > 1 ||
    					StringUtil.countCharacters(s, 'W') > 6 || StringUtil.countCharacters(s, '_') > 0 ) {
    					s = msgs[r.nextInt(msgs.length)]; //Picks random message from list
    				}
    			    for(int i =0; i < swear.length; i++)
    			    {
    			    	String tempS = s.toLowerCase();
    			        if(tempS.contains(swear[i]))
    			        {
    			        	s = msgs[r.nextInt(msgs.length)]; //Picks random message from list
    			        	break;
    			        }
    			    }
                chr.getMap().broadcastMessage(MaplePacketCreator.getChatText(chr.getId(), s, chr.isGM(), slea.readByte()));
            }
            else {
                chr.getMap().broadcastGMMessage(MaplePacketCreator.getChatText(chr.getId(), s, chr.isGM(), slea.readByte()));
            }
        }
    }
}

