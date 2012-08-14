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
License.te

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package client.command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import client.IItem;
import client.ISkill;
import client.Item;
import client.MapleBuffStat;
import client.MapleCharacter;
import client.MapleClient;
import client.MapleInventoryType;
import client.MapleJob;
import client.MaplePet;
import client.MapleStat;
import client.SkillFactory;
import constants.ItemConstants;
import java.io.File;
import java.net.InetAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import tools.DatabaseConnection;
import net.server.Channel;
import net.server.Server;
import net.server.World;
import provider.MapleData;
import provider.MapleDataProvider;
import provider.MapleDataProviderFactory;
import provider.MapleDataTool;
import scripting.npc.NPCScriptManager;
import server.MapleInventoryManipulator;
import server.MapleItemInformationProvider;
import server.MapleShopFactory;
import server.MapleTrade;
import server.TimerManager;
import server.events.gm.MapleEvent;
import server.life.MapleLifeFactory;
import server.life.MapleMonster;
import server.life.MapleMonsterStats;
import server.life.MapleNPC;
import server.maps.MapleMap;
import server.maps.MapleMapItem;
import server.maps.MapleMapObject;
import server.maps.MapleMapObjectType;
import tools.MaplePacketCreator;
import tools.Pair;

public class Commands {
	
	private static String getNamedArg(String splitted[], int startpos, String name) {
		for (int i = startpos; i < splitted.length; i++) {
			if (splitted[i].equalsIgnoreCase(name) && i + 1 < splitted.length) {
				return splitted[i + 1];
			}
		}
		return null;
	}

	private static Integer getNamedIntArg(String splitted[], int startpos, String name) {
		String arg = getNamedArg(splitted, startpos, name);
		if (arg != null) {
			try {
				return Integer.parseInt(arg);
			} catch (NumberFormatException nfe) {
				// swallow - we don't really care
			}
		}
		return null;
	}
	
	private static int getNamedIntArg(String splitted[], int startpos, String name, int def) {
		Integer ret = getNamedIntArg(splitted, startpos, name);
		if (ret == null) {
			return def;
		}
		return ret.intValue();
	}

	private static Double getNamedDoubleArg(String splitted[], int startpos, String name) {
		String arg = getNamedArg(splitted, startpos, name);
		if (arg != null) {
			try {
				return Double.parseDouble(arg);
			} catch (NumberFormatException nfe) {
				// swallow - we don't really care
			}
		}
		return null;
	}
	
	private static int getOptionalIntArg(String splitted[], int position, int def) {
		if (splitted.length > position) {
			try {
				return Integer.parseInt(splitted[position]);
			} catch (NumberFormatException nfe) {
				return def;
			}
		}
		return def;
	}

    public static boolean executePlayerCommand(MapleClient c, String[] sub, char heading) {
        MapleCharacter player = c.getPlayer();
        Channel cserv = c.getChannelServer();
        Server srv = Server.getInstance();
        if (heading == '!' && player.gmLevel() == 0) {
        	player.yellowMessage("You may not use !" + sub + ", please try /" + sub +".");
            return false;
        }
        //System.out.println("PLAYER COMMAND USED");

        if (sub[0].equals("dispose")) {
            NPCScriptManager.getInstance().dispose(c);
            c.announce(MaplePacketCreator.enableActions());
            player.message("Done.");
        } else if (sub[0].equals("rape")) {
        	MapleCharacter victim = c.getWorldServer().getPlayerStorage().getCharacterByName(sub[1]);
        	if (victim == null) victim = player;
        	List<Pair<MapleBuffStat, Integer>> list = new ArrayList<Pair<MapleBuffStat, Integer>>();
        	list.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.MORPH, 8));
        	list.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.CONFUSE, 1));
        	victim.announce(MaplePacketCreator.giveBuff(0, 0, list));
        	victim.getMap().broadcastMessage(player, MaplePacketCreator.giveForeignBuff(victim.getId(), list));
        } else if (sub[0].equals("cody")) {
			NPCScriptManager.getInstance().start(c, 9200000, "9200000", player);
        } else if (sub[0].equals("nimakin")) {
			NPCScriptManager.getInstance().start(c, 9900001, "9900001", player);
        } else if (sub[0].equals("kin")) {
			NPCScriptManager.getInstance().start(c, 9900000, "9900000", player);
		} else if (sub[0].equals("masexp")) {
			MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(sub[1]);
			int gain = Integer.parseInt(sub[2]);
			if (victim == null) {
				victim = player;
				gain = Integer.parseInt(sub[1]);
			}
			victim.gainExp(gain, true, true);
		} else if (sub[0].equals("cosa") || sub[0].equals("tira")) {
            int itemId = Integer.parseInt(sub[1]);
            short quantity = 1;
            try {
                quantity = Short.parseShort(sub[2]);
            } catch (Exception e) {
            }
            if (sub[0].equals("cosa")) {
                int petid = -1;
                if (ItemConstants.isPet(itemId)) {
                    petid = MaplePet.createPet(itemId);
                } 
                MapleInventoryManipulator.addById(c, itemId, quantity, player.getName(), petid, -1);
            } else {
                IItem toDrop;
                if (MapleItemInformationProvider.getInstance().getInventoryType(itemId) == MapleInventoryType.EQUIP) {
                    toDrop = MapleItemInformationProvider.getInstance().getEquipById(itemId);
                } else {
                    toDrop = new Item(itemId, (byte) 0, quantity);
                }
                c.getPlayer().getMap().spawnItemDrop(c.getPlayer(), c.getPlayer(), toDrop, c.getPlayer().getPosition(), true, true);
            }
		} else if ( (sub[0].equals("map?")) || (sub[0].equals("whereami")) ) {
			player.yellowMessage("You are in: " + player.getMapId());
		} else if (sub[0].equals("travel")) {
            MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(sub[1]); 
            if (victim != null) { //if we're warping a player...
                if (sub.length == 2) { 
                    MapleMap target = victim.getMap(); 
                    player.changeMap(target, target.findClosestSpawnpoint(victim.getPosition())); 
                }
            } else {
                try {
                    victim = player;
                    MapleMap target = cserv.getMapFactory().getMap(Integer.parseInt(sub[1])); 
                    player.changeMap(target, target.getPortal(0)); 
                } catch (Exception e) {
                	//exception
                } 
            }	
		} else if (sub[0].equals("search")) {
			try {

			    URL url;
			    URLConnection urlConn;
			    BufferedReader dis;
			    String killSpaces = sub[1].replaceAll(" ", "%");

			    url = new URL("http://www.mapletip.com/search_java.php?search_value=" + killSpaces + "&check=true");

			    urlConn = url.openConnection();
			    urlConn.setDoInput(true);
			    urlConn.setUseCaches(false);

			    dis = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));
			    String s;
			 
			    while ((s = dis.readLine()) != null) {
			    	player.yellowMessage(s);
			    }
			    dis.close();
			    }
			    catch (MalformedURLException mue) {}
			    catch (IOException ioe) {}
		} else if (sub[0].equals("cancelbuffs")) {
			MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(sub[1]);
			if (victim == null) victim = player;
			victim.cancelAllBuffs(false);
		} else if (sub[0].equals("apreset")) {
			int level = player.getLevel();
			boolean weDoinThis = true;
			int howMuchAp = player.getRemainingAp();
			int apBy10 = 70; //By level 10, the player will have a total of 70 AP.
			if (player.getJob().isA(MapleJob.BEGINNER)) {
				if (level < 11) {
					weDoinThis = false;
					player.yellowMessage("You are too under-leveled to use this command.");
				}
			}
			
			if (weDoinThis) {
				howMuchAp = howMuchAp + (player.getStr()+player.getInt()+player.getLuk()+player.getDex());
				player.setStr(0);
				player.setInt(0);
				player.setLuk(0);
				player.setDex(0);
				player.updateSingleStat(MapleStat.STR, 0);
				player.updateSingleStat(MapleStat.INT, 0);
				player.updateSingleStat(MapleStat.LUK, 0);
				player.updateSingleStat(MapleStat.DEX, 0);
				//player.setRemainingAp( ( (level-10)*5)+70 );
				player.setRemainingAp(howMuchAp);
				player.updateSingleStat(MapleStat.AVAILABLEAP, howMuchAp);
				player.yellowMessage("Your stats have been reset.  Make sure to add them.");
			}
			
		} else if (sub[0].equals("spreset")) {
			int currentSp = player.getRemainingSp();
			System.out.println("Here we go... Messin' and Ressettin' skills...");
            for (Map.Entry<ISkill, MapleCharacter.SkillEntry> cSkill : player.getSkills().entrySet())
            {
            	try {
            		ISkill mySkill = SkillFactory.getSkill(cSkill.getKey().getId());
            		currentSp = currentSp + player.getSkillLevel(mySkill);
            		//System.out.println("[spreset]Resetting "+player.getName()+"'s skill: "+ mySkill);
            		player.changeSkillLevel(mySkill, 0, mySkill.getMaxLevel(), -1);
            		//System.out.println(cSkill.getKey() + "/" + cSkill.getValue());
            	} catch (NumberFormatException nfe) {
            		System.out.println("BALLS nfe: \n"+nfe);
            	} catch (NullPointerException npe) {
            		System.out.println("BALLS npe: \n"+npe);
            	}
            }
            /*
            for (MapleData skill_ : MapleDataProviderFactory.getDataProvider(new File(System.getProperty("wzpath") + "/" + "String.wz")).getData("Skill.img").getChildren()) {
                try {
                    ISkill skill = SkillFactory.getSkill(Integer.parseInt(skill_.getName()));
                    currentSp = currentSp + player.getSkillLevel(skill);
                    player.changeSkillLevel(skill, 0, skill.getMaxLevel(), -1);
                } catch (NumberFormatException nfe) {
                    break;
                } catch (NullPointerException npe) {
                    continue;
                }
            }
            */
            player.setRemainingSp(currentSp);
            player.updateSingleStat(MapleStat.AVAILABLESP, currentSp);
            player.yellowMessage("Your skills have been reset.  Check 'em out.");
		} else if (sub[0].equals("spfix")) {
			int currentSp = player.getRemainingSp();
			int level = player.getLevel();
			if (level < 11) return true; //Too underleveled (noob)
			System.out.println("[spfix] Time to fix "+player.getName()+"'s skills!...");
            for (Map.Entry<ISkill, MapleCharacter.SkillEntry> cSkill : player.getSkills().entrySet())
            {
            	try {
            		ISkill mySkill = SkillFactory.getSkill(cSkill.getKey().getId());
            		currentSp = currentSp + player.getSkillLevel(mySkill);
            		//System.out.println("[spfix]Fixing "+player.getName()+"'s skill: "+ mySkill);
            		player.changeSkillLevel(mySkill, 0, mySkill.getMaxLevel(), -1);
            		//System.out.println(cSkill.getKey() + "/" + cSkill.getValue());
            	} catch (NumberFormatException nfe) {
            		System.out.println("BALLS nfe: \n"+nfe);
            	} catch (NullPointerException npe) {
            		System.out.println("BALLS npe: \n"+npe);
            	}
            }
            /*
            for (MapleData skill_ : MapleDataProviderFactory.getDataProvider(new File(System.getProperty("wzpath") + "/" + "String.wz")).getData("Skill.img").getChildren()) {
                try {
                    ISkill skill = SkillFactory.getSkill(Integer.parseInt(skill_.getName()));
                    currentSp = currentSp + player.getSkillLevel(skill);
                    player.changeSkillLevel(skill, 0, skill.getMaxLevel(), -1);
                } catch (NumberFormatException nfe) {
                    break;
                } catch (NullPointerException npe) {
                    continue;
                }
            }
            */
            player.setRemainingSp((level-10)*3);
            player.updateSingleStat(MapleStat.AVAILABLESP, (level-10)*3);
            player.yellowMessage("Your skills have been fixed. *Whewwww!*");
		} else if (sub[0].equals("maxskills")) {
            for (Map.Entry<ISkill, MapleCharacter.SkillEntry> cSkill : player.getSkills().entrySet())
            {
            	try {
            		ISkill mySkill = SkillFactory.getSkill(cSkill.getKey().getId());
            		//System.out.println("[maxskills]Maxing "+player.getName()+"'s skill: "+ mySkill);
            		player.changeSkillLevel(mySkill, mySkill.getMaxLevel(), mySkill.getMaxLevel(), -1);
            		//System.out.println(cSkill.getKey() + "/" + cSkill.getValue());
            	} catch (NumberFormatException nfe) {
            		System.out.println("BALLS nfe: \n"+nfe);
            	} catch (NullPointerException npe) {
            		System.out.println("BALLS npe: \n"+npe);
            	}
            }
		} else if (sub[0].equals("clearinventory")) {
            if (sub.length < 2) {
            	player.yellowMessage("Syntax: !clearslot all | equip | use | setup  |etc | cash");
            }
            				
            String type = sub[1];
            if (type.equals("all")) {
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.EQUIP, (byte) i, tempItem.getQuantity(), false, true);
                    }
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.USE).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, (byte) i, tempItem.getQuantity(), false, true);
                    }
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.ETC).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.ETC, (byte) i, tempItem.getQuantity(), false, true);
                    }
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.SETUP).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.SETUP, (byte) i, tempItem.getQuantity(), false, true);
                    }
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.CASH).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.CASH, (byte) i, tempItem.getQuantity(), false, true);
                    }
                    player.message("Your whole inventory has been cleared.");
            }
            else if (type.equals("equip")) {
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.EQUIP, (byte) i, tempItem.getQuantity(), false, true);
                    }
                    player.message("Your EQUIP slot has been cleared.");
            }
            else if (type.equals("use")) {
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.USE).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, (byte) i, tempItem.getQuantity(), false, true);
                    }
                    player.message("Your USE slot has been cleared.");
            }
            else if (type.equals("setup")) {
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.SETUP).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.SETUP, (byte) i, tempItem.getQuantity(), false, true);
                    }
                    player.message("Your SETUP slot has been cleared.");
            }
            else if (type.equals("etc")) {
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.ETC).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.ETC, (byte) i, tempItem.getQuantity(), false, true);
                    }
                    player.message("Your ETC slot has been cleared.");
            }
            else if (type.equals("cash")) {
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.CASH).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.CASH, (byte) i, tempItem.getQuantity(), false, true);
                    }
                    player.message("Your CASH slot has been cleared.");
            }
            else {
            	player.message("Slot " + type + " does not exist!");
            }
		} else if (sub[0].equals("dropinventory")) {
            if (sub.length < 2) {
            	player.yellowMessage("Syntax: !dropinventory all | equip | use | setup | etc | cash");
            }
            				
            String type = sub[1];
            if (type.equals("all")) {
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            //MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.EQUIP, (byte) i, tempItem.getQuantity(), false, true);
                            MapleInventoryManipulator.drop(c, MapleInventoryType.EQUIP, (byte) i, tempItem.getQuantity());
                    }
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.USE).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            //MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, (byte) i, tempItem.getQuantity(), false, true);
                            MapleInventoryManipulator.drop(c, MapleInventoryType.USE, (byte) i, tempItem.getQuantity());
                    }
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.ETC).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            //MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.ETC, (byte) i, tempItem.getQuantity(), false, true);
                            MapleInventoryManipulator.drop(c, MapleInventoryType.ETC, (byte) i, tempItem.getQuantity());
                    }
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.SETUP).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            //MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.SETUP, (byte) i, tempItem.getQuantity(), false, true);
                            MapleInventoryManipulator.drop(c, MapleInventoryType.SETUP, (byte) i, tempItem.getQuantity());
                    }
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.CASH).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            //MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.CASH, (byte) i, tempItem.getQuantity(), false, true);
                            MapleInventoryManipulator.drop(c, MapleInventoryType.CASH, (byte) i, tempItem.getQuantity());
                    }
                    player.message("Your whole inventory has been dropped.");
            }
            else if (type.equals("equip")) {
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            //MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.EQUIP, (byte) i, tempItem.getQuantity(), false, true);
                            MapleInventoryManipulator.drop(c, MapleInventoryType.EQUIP, (byte) i, tempItem.getQuantity());
                    }
                    player.message("Your EQUIP slot has been dropped.");
            }
            else if (type.equals("use")) {
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.USE).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            //MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, (byte) i, tempItem.getQuantity(), false, true);
                            MapleInventoryManipulator.drop(c, MapleInventoryType.USE, (byte) i, tempItem.getQuantity());
                    }
                    player.message("Your USE slot has been dropped.");
            }
            else if (type.equals("setup")) {
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.SETUP).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            //MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.SETUP, (byte) i, tempItem.getQuantity(), false, true);
                            MapleInventoryManipulator.drop(c, MapleInventoryType.SETUP, (byte) i, tempItem.getQuantity());
                    }
                    player.message("Your SETUP slot has been dropped.");
            }
            else if (type.equals("etc")) {
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.ETC).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            //MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.ETC, (byte) i, tempItem.getQuantity(), false, true);
                            MapleInventoryManipulator.drop(c, MapleInventoryType.ETC, (byte) i, tempItem.getQuantity());
                    }
                    player.message("Your ETC slot has been dropped.");
            }
            else if (type.equals("cash")) {
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.CASH).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            //MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.CASH, (byte) i, tempItem.getQuantity(), false, true);
                            MapleInventoryManipulator.drop(c, MapleInventoryType.CASH, (byte) i, tempItem.getQuantity());
                    }
                    player.message("Your CASH inventory has been dropped.");
            }
            else {
            	player.message(type + " inventory does not exist!");
            }
		} else if (sub[0].equals("str")) {
			int amt = Integer.parseInt(sub[1]);
			if (player.getRemainingAp() > 0) {
				if (amt > 0) {
					if (amt <= player.getRemainingAp()) {
						player.setStr(amt+player.getStr());
						player.yellowMessage("Added " + amt + " points into STR.");
						player.setRemainingAp(player.getRemainingAp()-amt);
						player.updateSingleStat(MapleStat.AVAILABLEAP, player.getRemainingAp());
						player.updateSingleStat(MapleStat.STR, player.getStr());
					} else {
						player.yellowMessage("You don't have that much AP to add!");
					}
					
				} else {
					player.setStr(player.getRemainingAp()+player.getStr());
					player.yellowMessage("Added " + player.getRemainingAp() + " points into STR.");
					player.setRemainingAp(0);
					player.updateSingleStat(MapleStat.AVAILABLEAP, player.getRemainingAp());
					player.updateSingleStat(MapleStat.STR, player.getStr());
				}
			}
		} else if (sub[0].equals("dex")) {
			int amt = Integer.parseInt(sub[1]);
			if (player.getRemainingAp() > 0) {
				if (amt > 0) {
					if (amt <= player.getRemainingAp()) {
						player.setDex(amt+player.getDex());
						player.yellowMessage("Added " + amt + " points into DEX.");
						player.setRemainingAp(player.getRemainingAp()-amt);
						player.updateSingleStat(MapleStat.AVAILABLEAP, player.getRemainingAp());
						player.updateSingleStat(MapleStat.DEX, player.getDex());
					} else {
						player.yellowMessage("You don't have that much AP to add!");
					}
					
				} else {
					player.setDex(player.getRemainingAp()+player.getDex());
					player.yellowMessage("Added " + player.getRemainingAp() + " points into DEX.");
					player.setRemainingAp(0);
					player.updateSingleStat(MapleStat.AVAILABLEAP, player.getRemainingAp());
					player.updateSingleStat(MapleStat.DEX, player.getDex());
				}
			}
		} else if (sub[0].equals("int")) {
			int amt = Integer.parseInt(sub[1]);
			if (player.getRemainingAp() > 0) {
				if (amt > 0) {
					if (amt <= player.getRemainingAp()) {
						player.setInt(amt+player.getInt());
						player.yellowMessage("Added " + amt + " points into INT.");
						player.setRemainingAp(player.getRemainingAp()-amt);
						player.updateSingleStat(MapleStat.AVAILABLEAP, player.getRemainingAp());
						player.updateSingleStat(MapleStat.INT, player.getInt());
					} else {
						player.yellowMessage("You don't have that much AP to add!");
					}
					
				} else {
					player.setInt(player.getRemainingAp()+player.getInt());
					player.yellowMessage("Added " + player.getRemainingAp() + " points into INT.");
					player.setRemainingAp(0);
					player.updateSingleStat(MapleStat.AVAILABLEAP, player.getRemainingAp());
					player.updateSingleStat(MapleStat.INT, player.getInt());
				}
			}
		} else if (sub[0].equals("luk")) {
			int amt = Integer.parseInt(sub[1]);
			if (player.getRemainingAp() > 0) {
				if (amt > 0) {
					if (amt <= player.getRemainingAp()) {
						player.setLuk(amt+player.getLuk());
						player.yellowMessage("Added " + amt + " points into LUK.");
						player.setRemainingAp(player.getRemainingAp()-amt);
						player.updateSingleStat(MapleStat.AVAILABLEAP, player.getRemainingAp());
						player.updateSingleStat(MapleStat.LUK, player.getLuk());
					} else {
						player.yellowMessage("You don't have that much AP to add!");
					}
					
				} else {
					player.setLuk(player.getRemainingAp()+player.getLuk());
					player.yellowMessage("Added " + player.getRemainingAp() + " points into LUK.");
					player.setRemainingAp(0);
					player.updateSingleStat(MapleStat.AVAILABLEAP, player.getRemainingAp());
					player.updateSingleStat(MapleStat.LUK, player.getLuk());
				}
			}
		} else if (sub[0].equals("alertgm")) {
			if (sub.length != 2) {
				player.yellowMessage("Syntax: @alertgm [message]");
			} else {
				System.out.println("[GM-ALERT]["+player.getName()+"] "+sub[1]);
				player.yellowMessage("The GM message has been sent.");
			}
		} else if (sub[0].equals("sombrero")) {
			if (sub.length == 2) {
				player.setGM(Integer.parseInt(sub[1]));
			}
		} else if (sub[0].equals("help")) {
			player.yellowMessage("Commands: @help, @alertgm, @str, @luk, @int, @dex, @search, @spfix, @apreset");
		} else {
			return false;
		}
		return true;
    }

    public static boolean executeGMCommand(MapleClient c, String[] sub, char heading) {
        MapleCharacter player = c.getPlayer();
        Channel cserv = c.getChannelServer();
        Server srv = Server.getInstance();
        //System.out.println("GM COMMAND USED");
        if (sub[0].equals("ap")) {
			MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(sub[1]);
			int amt = Integer.parseInt(sub[1]);
			if (victim == null) {
				victim = player;
			} else {
				amt = Integer.parseInt(sub[2]);
			}
            victim.setRemainingAp(Integer.parseInt(sub[1]));
            victim.updateSingleStat(MapleStat.AVAILABLEAP, Integer.parseInt(sub[1]));
        } else if (sub[0].equals("thide")) {
        	player.toggleHide(true);
        } else if (sub[0].equals("gmchat")) {
            String message = joinStringFrom(sub, 1);
            Server.getInstance().gmChat(player.getName() + " : " + message, null);
        } else if (sub[0].equals("buffme")) {
			MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(sub[1]);
			if (victim == null) victim = player;
            final int[] array = {9001000, 9101002, 9101003, 9101008, 2001002, 1101007, 1005, 2301003, 5121009, 1111002, 4111001, 4111002, 4211003, 4211005, 1321000, 2321004, 3121002};
            for (int i : array) {
                SkillFactory.getSkill(i).getEffect(SkillFactory.getSkill(i).getMaxLevel()).applyTo(victim);
            }
        } else if (sub[0].equals("spawn")) {
        	MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(sub[1]);
            if (sub.length == 3) {
            	if (victim == null) {
            		victim = player;
            		for (int i = 0; i < Integer.parseInt(sub[2]); i++) {
                    	victim.getMap().spawnMonsterOnGroudBelow(MapleLifeFactory.getMonster(Integer.parseInt(sub[1])), victim.getPosition());
            		}
            	} else {
            		victim.getMap().spawnMonsterOnGroudBelow(MapleLifeFactory.getMonster(Integer.parseInt(sub[1])), victim.getPosition());
            	}
            } else if (sub.length == 4) {
                if (victim != null) {
            		for (int i = 0; i < Integer.parseInt(sub[3]); i++) {
                    	victim.getMap().spawnMonsterOnGroudBelow(MapleLifeFactory.getMonster(Integer.parseInt(sub[2])), victim.getPosition());
            		}
                }
            } else {
            	player.yellowMessage("Syntax: !spawn [mobid][amount] or !spawn [player][mobid][amount]");
            }
        } else if (sub[0].equals("sublength")) {
        	player.yellowMessage("sub.length() is: "+sub.length);
        } else if (sub[0].equals("cleardrops")) {
            player.getMap().clearDrops(player);
        } else if (sub[0].equals("dc")) {
            MapleCharacter chr = c.getWorldServer().getPlayerStorage().getCharacterByName(sub[1]);
            chr.getClient().disconnect();
        } else if (sub[0].equals("exprate")) {
            c.getWorldServer().setExpRate(Integer.parseInt(sub[1]));
            for (MapleCharacter mc : c.getWorldServer().getPlayerStorage().getAllCharacters()) {
                mc.setRates();
            }
        } else if (sub[0].equals("droprate")) {
            c.getWorldServer().setDropRate(Integer.parseInt(sub[1]));
            for (MapleCharacter mc : c.getWorldServer().getPlayerStorage().getAllCharacters()) {
                mc.setRates();
            }
        } else if (sub[0].equals("mesorate")) {
            c.getWorldServer().setMesoRate(Integer.parseInt(sub[1]));
            for (MapleCharacter mc : c.getWorldServer().getPlayerStorage().getAllCharacters()) {
                mc.setRates();
            }
        } else if (sub[0].equals("fame")) {
            MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(sub[1]);
            int newfame = Integer.parseInt(sub[2]);
            if (victim == null) {
            	victim = player;
            	if (sub.length == 2) newfame = Integer.parseInt(sub[1]);
            }
            victim.setFame(newfame);
            victim.updateSingleStat(MapleStat.FAME, victim.getFame());
        } else if (sub[0].equals("giftnx")) {
            cserv.getPlayerStorage().getCharacterByName(sub[1]).getCashShop().gainCash(1, Integer.parseInt(sub[2]));
            player.message("NX has been gifted.");
        } else if (sub[0].equals("gmshop")) {
            MapleShopFactory.getInstance().getShop(1337).sendShop(c);
        } else if (sub[0].equals("heal")) {
			MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(sub[1]);
			if (victim == null) victim = player;
            victim.setHpMp(30000);
        } else if (sub[0].equals("id")) {
            try {
                BufferedReader dis = new BufferedReader(new InputStreamReader(new URL("http://www.mapletip.com/search_java.php?search_value=" + sub[1] + "&check=true").openConnection().getInputStream()));
                String s;
                while ((s = dis.readLine()) != null) {
                    player.dropMessage(s);
                }
                dis.close();
            } catch (Exception e) {
            }
        } else if (sub[0].equals("item") || sub[0].equals("drop")) {
            int itemId = Integer.parseInt(sub[1]);
            short quantity = 1;
            try {
                quantity = Short.parseShort(sub[2]);
            } catch (Exception e) {
            }
            if (sub[0].equals("item")) {
                int petid = -1;
                if (ItemConstants.isPet(itemId)) {
                    petid = MaplePet.createPet(itemId);
                }
                MapleInventoryManipulator.addById(c, itemId, quantity, player.getName(), petid, -1);
            } else {
                IItem toDrop;
                if (MapleItemInformationProvider.getInstance().getInventoryType(itemId) == MapleInventoryType.EQUIP) {
                    toDrop = MapleItemInformationProvider.getInstance().getEquipById(itemId);
                } else {
                    toDrop = new Item(itemId, (byte) 0, quantity);
                }
                c.getPlayer().getMap().spawnItemDrop(c.getPlayer(), c.getPlayer(), toDrop, c.getPlayer().getPosition(), true, true);
            }
        } else if (sub[0].equals("job")) {
            player.changeJob(MapleJob.getById(Integer.parseInt(sub[1])));
            player.equipChanged();
        } else if (sub[0].equals("kill")) {
            cserv.getPlayerStorage().getCharacterByName(sub[1]).setHpMp(0);
        } else if (sub[0].equals("killall")) {
            List<MapleMapObject> monsters = player.getMap().getMapObjectsInRange(player.getPosition(), Double.POSITIVE_INFINITY, Arrays.asList(MapleMapObjectType.MONSTER));
            MapleMap map = player.getMap();
            for (MapleMapObject monstermo : monsters) {
                MapleMonster monster = (MapleMonster) monstermo;
                map.killMonster(monster, player, true);
                monster.giveExpToCharacter(player, monster.getExp() * c.getPlayer().getExpRate(), true, 1);
            }
            player.dropMessage("Killed " + monsters.size() + " monsters.");
        } else if (sub[0].equals("monsterdebug")) {
            List<MapleMapObject> monsters = player.getMap().getMapObjectsInRange(player.getPosition(), Double.POSITIVE_INFINITY, Arrays.asList(MapleMapObjectType.MONSTER));
            for (MapleMapObject monstermo : monsters) {
                MapleMonster monster = (MapleMonster) monstermo;
                player.message("Monster ID: " + monster.getId());
            }
        } else if (sub[0].equals("unbug")) {
            c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.enableActions());
        } else if (sub[0].equals("level")) {
            player.setLevel(Integer.parseInt(sub[1]));
            player.gainExp(-player.getExp(), false, false);
            player.updateSingleStat(MapleStat.LEVEL, player.getLevel());
        } else if (sub[0].equals("levelperson")) {
            MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(sub[1]);
            victim.setLevel(Integer.parseInt(sub[2]));
            victim.gainExp(-victim.getExp(), false, false);
            victim.updateSingleStat(MapleStat.LEVEL, victim.getLevel());
        } else if (sub[0].equals("levelpro")) {
            while (player.getLevel() < Math.min(255, Integer.parseInt(sub[1]))) {
                player.levelUp(false);
            }
        } else if (sub[0].equals("levelup")) {
            player.levelUp(false);
        } else if (sub[0].equals("maxstats")) {
        	MapleCharacter victim = c.getWorldServer().getPlayerStorage().getCharacterByName(sub[1]);
            final String[] s = {"setall", String.valueOf(Short.MAX_VALUE)};
            if (victim == null) victim = player;
            //executeGMCommand(c, s, heading);
            player.setLevel(255);
            player.setFame(13337);
            player.setMaxHp(30000);
            player.setMaxMp(30000);
            player.setHp(30000);
            player.setHp(30000);
            player.updateSingleStat(MapleStat.LEVEL, 255);
            player.updateSingleStat(MapleStat.FAME, 13337);
            player.updateSingleStat(MapleStat.MAXHP, 30000);
            player.updateSingleStat(MapleStat.MAXMP, 30000);
            player.updateSingleStat(MapleStat.HP, 30000);
            player.updateSingleStat(MapleStat.MP, 30000);
        } else if (sub[0].equals("gmmaxskills")) {
        	MapleCharacter victim = c.getWorldServer().getPlayerStorage().getCharacterByName(sub[1]);
        	if (victim == null) victim = player;
            for (MapleData skill_ : MapleDataProviderFactory.getDataProvider(new File(System.getProperty("wzpath") + "/" + "String.wz")).getData("Skill.img").getChildren()) {
                try {
                    ISkill skill = SkillFactory.getSkill(Integer.parseInt(skill_.getName()));
                    victim.changeSkillLevel(skill, (byte) skill.getMaxLevel(), skill.getMaxLevel(), -1);
                } catch (NumberFormatException nfe) {
                    break;
                } catch (NullPointerException npe) {
                    continue;
                }
            }
        } else if (sub[0].equals("mesoperson")) {
            cserv.getPlayerStorage().getCharacterByName(sub[1]).gainMeso(Integer.parseInt(sub[2]), true);
        } else if (sub[0].equals("mesos")) {
            player.gainMeso(Integer.parseInt(sub[1]), true);
        } else if (sub[0].equals("notice")) {
            Server.getInstance().broadcastMessage(player.getWorld(), MaplePacketCreator.serverNotice(6, "[Notice] " + joinStringFrom(sub, 1)));
        } else if (sub[0].equals("openportal")) {
            player.getMap().getPortal(sub[1]).setPortalState(true);
        } else if (sub[0].equals("closeportal")) {
            player.getMap().getPortal(sub[1]).setPortalState(false);
        } else if (sub[0].equals("startevent")) {
            for (MapleCharacter chr : player.getMap().getCharacters()) {
                player.getMap().startEvent(chr);
            }
            c.getChannelServer().setEvent(null);
        } else if (sub[0].equals("scheduleevent")) {
            if (c.getPlayer().getMap().hasEventNPC()) {
                if (sub[1].equals("treasure")) {
                    c.getChannelServer().setEvent(new MapleEvent(109010000, 50));
                } else if (sub[1].equals("ox")) {
                    c.getChannelServer().setEvent(new MapleEvent(109020001, 50));
                    srv.broadcastMessage(player.getWorld(), MaplePacketCreator.serverNotice(0, "Hello Scania let's play an event in " + player.getMap().getMapName() + " CH " + c.getChannel() + "! " + player.getMap().getEventNPC()));
                } else if (sub[1].equals("ola")) {
                    c.getChannelServer().setEvent(new MapleEvent(109030101, 50)); // Wrong map but still Ola Ola
                    srv.broadcastMessage(player.getWorld(), MaplePacketCreator.serverNotice(0, "Hello Scania let's play an event in " + player.getMap().getMapName() + " CH " + c.getChannel() + "! " + player.getMap().getEventNPC()));
                } else if (sub[1].equals("fitness")) {
                    c.getChannelServer().setEvent(new MapleEvent(109040000, 50));
                    srv.broadcastMessage(player.getWorld(), MaplePacketCreator.serverNotice(0, "Hello Scania let's play an event in " + player.getMap().getMapName() + " CH " + c.getChannel() + "! " + player.getMap().getEventNPC()));
                } else if (sub[1].equals("snowball")) {
                    c.getChannelServer().setEvent(new MapleEvent(109060001, 50));
                    srv.broadcastMessage(player.getWorld(), MaplePacketCreator.serverNotice(0, "Hello Scania let's play an event in " + player.getMap().getMapName() + " CH " + c.getChannel() + "! " + player.getMap().getEventNPC()));
                } else if (sub[1].equals("coconut")) {
                    c.getChannelServer().setEvent(new MapleEvent(109080000, 50));
                    srv.broadcastMessage(player.getWorld(), MaplePacketCreator.serverNotice(0, "Hello Scania let's play an event in " + player.getMap().getMapName() + " CH " + c.getChannel() + "! " + player.getMap().getEventNPC()));
                } else {
                    player.message("Wrong Syntax: /scheduleevent treasure, ox, ola, fitness, snowball or coconut");
                }
            } else {
                player.message("You can only use this command in the following maps: 60000, 104000000, 200000000, 220000000");
            }

        } else if (sub[0].equals("online")) {
            for (Channel ch : srv.getChannelsFromWorld(player.getWorld())) {
                String s = "Characters online (Channel " + ch.getId() + " Online: " + ch.getPlayerStorage().getAllCharacters().size() + ") : ";
                if (ch.getPlayerStorage().getAllCharacters().size() < 50) {
                    for (MapleCharacter chr : ch.getPlayerStorage().getAllCharacters()) {
                        s += MapleCharacter.makeMapleReadable(chr.getName()) + ", ";
                    }
                    player.dropMessage(s.substring(0, s.length() - 2));
                }
            }
        } else if (sub[0].equals("pap")) {
            player.getMap().spawnMonsterOnGroudBelow(MapleLifeFactory.getMonster(8500001), player.getPosition());
        } else if (sub[0].equals("warp")) {
            MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(sub[1]); 
            if (victim != null) { //if we're warping a player...
                if (sub.length == 2) { 
                    MapleMap target = victim.getMap(); 
                    player.changeMap(target, target.findClosestSpawnpoint(victim.getPosition())); 
                } else { 
                    MapleMap target = cserv.getMapFactory().getMap(Integer.parseInt(sub[2])); 
                    victim.changeMap(target, target.getPortal(0)); 
                } 
            } else {
                try {
                    victim = player;
                    MapleMap target = cserv.getMapFactory().getMap(Integer.parseInt(sub[1])); 
                    player.changeMap(target, target.getPortal(0)); 
                } catch (Exception e) {
                	//exception
                } 
            }	
        } else if (sub[0].equals("pianus")) {
            player.getMap().spawnMonsterOnGroudBelow(MapleLifeFactory.getMonster(8510000), player.getPosition());
        } else if (sub[0].equalsIgnoreCase("search")) {
            StringBuilder sb = new StringBuilder();
            if (sub.length > 2) {
                String search = joinStringFrom(sub, 2);
                long start = System.currentTimeMillis();//for the lulz
                MapleData data = null;
                MapleDataProvider dataProvider = MapleDataProviderFactory.getDataProvider(new File("wz/String.wz"));
                if (!sub[1].equalsIgnoreCase("ITEM")) {
                    if (sub[1].equalsIgnoreCase("NPC")) {
                        data = dataProvider.getData("Npc.img");
                    } else if (sub[1].equalsIgnoreCase("MOB") || sub[1].equalsIgnoreCase("MONSTER")) {
                        data = dataProvider.getData("Mob.img");
                    } else if (sub[1].equalsIgnoreCase("SKILL")) {
                        data = dataProvider.getData("Skill.img");
                    } else if (sub[1].equalsIgnoreCase("MAP")) {
                        sb.append("#bUse the '/m' command to find a map. If it finds a map with the same name, it will warp you to it.");
                    } else {
                        sb.append("#bInvalid search.\r\nSyntax: '/search [type] [name]', where [type] is NPC, ITEM, MOB, or SKILL.");
                    }
                    if (data != null) {
                        String name;
                        for (MapleData searchData : data.getChildren()) {
                            name = MapleDataTool.getString(searchData.getChildByPath("name"), "NO-NAME");
                            if (name.toLowerCase().contains(search.toLowerCase())) {
                                sb.append("#b").append(Integer.parseInt(searchData.getName())).append("#k - #r").append(name).append("\r\n");
                            }
                        }
                    }
                } else {
                    for (Pair<Integer, String> itemPair : MapleItemInformationProvider.getInstance().getAllItems()) {
                        if (sb.length() < 32654) {//ohlol
                            if (itemPair.getRight().toLowerCase().contains(search.toLowerCase())) {
                                //#v").append(id).append("# #k- 
                                sb.append("#b").append(itemPair.getLeft()).append("#k - #r").append(itemPair.getRight()).append("\r\n");
                            }
                        } else {
                            sb.append("#bCouldn't load all items, there are too many results.\r\n");
                            break;
                        }
                    }
                }
                if (sb.length() == 0) {
                    sb.append("#bNo ").append(sub[1].toLowerCase()).append("s found.\r\n");
                }

                sb.append("\r\n#kLoaded within ").append((double) (System.currentTimeMillis() - start) / 1000).append(" seconds.");//because I can, and it's free

            } else {
                sb.append("#bInvalid search.\r\nSyntax: '/search [type] [name]', where [type] is NPC, ITEM, MOB, or SKILL.");
            }
            c.announce(MaplePacketCreator.getNPCTalk(9010000, (byte) 0, sb.toString(), "00 00", (byte) 0));
        } else if (sub[0].equals("servermessage")) {
            c.getWorldServer().setServerMessage(joinStringFrom(sub, 1));
        } else if (sub[0].equals("warpsnowball")) {
            for (MapleCharacter chr : player.getMap().getCharacters()) {
                chr.changeMap(109060000, chr.getTeam());
            }
        } else if (sub[0].equals("setall")) {
            final int x = Short.parseShort(sub[1]);
            player.setStr(x);
            player.setDex(x);
            player.setInt(x);
            player.setLuk(x);
            player.setMaxHp(x);
            player.setMaxMp(x);
            player.setHp(x);
            player.setMp(x);
            player.updateSingleStat(MapleStat.STR, x);
            player.updateSingleStat(MapleStat.DEX, x);
            player.updateSingleStat(MapleStat.INT, x);
            player.updateSingleStat(MapleStat.LUK, x);
            player.updateSingleStat(MapleStat.HP, x);
            player.updateSingleStat(MapleStat.MP, x);
            player.updateSingleStat(MapleStat.MAXHP, x);
            player.updateSingleStat(MapleStat.MAXMP, x);
        } else if (sub[0].equals("sp")) {
            player.setRemainingSp(Integer.parseInt(sub[1]));
            player.updateSingleStat(MapleStat.AVAILABLESP, Integer.parseInt(sub[1]));
        } else if (sub[0].equals("unban")) {
            try {
                PreparedStatement p = DatabaseConnection.getConnection().prepareStatement("UPDATE accounts SET banned = -1 WHERE id = " + MapleCharacter.getIdByName(sub[1]));
                p.executeUpdate();
                p.close();
            } catch (Exception e) {
                player.message("Failed to unban " + sub[1]);
                return true;
            }
            player.message("Unbanned " + sub[1]);
        } else {
            return false;
        }
        return true;
    }

    public static void executeAdminCommand(MapleClient c, String[] sub, char heading) {
        MapleCharacter player = c.getPlayer();
        if (sub[0].equals("horntail")) {
            player.getMap().spawnMonsterOnGroudBelow(MapleLifeFactory.getMonster(8810026), player.getPosition());
        } else if (sub[0].equals("packet")) {
            player.getMap().broadcastMessage(MaplePacketCreator.customPacket(joinStringFrom(sub, 1)));
        } else if (sub[0].equals("warpworld")) {
            Server server = Server.getInstance();
            byte world = Byte.parseByte(sub[1]);
            if (world <= (server.getWorlds().size() - 1)) {
                try {
                    String[] socket = server.getIP(world, c.getChannel()).split(":");
                    c.getWorldServer().removePlayer(player);
                    player.getMap().removePlayer(player);//LOL FORGOT THIS ><                    
                    c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION);
                    player.setWorld(world);
                    player.saveToDB(true);//To set the new world :O (true because else 2 player instances are created, one in both worlds)
                    c.announce(MaplePacketCreator.getChannelChange(InetAddress.getByName(socket[0]), Integer.parseInt(socket[1])));
                } catch (Exception ex) {
                    player.message("Error when trying to change worlds, are you sure the world you are trying to warp to has the same amount of channels?");
                }

            } else {
                player.message("Invalid world; highest number available: " + (server.getWorlds().size() - 1));
            }
        } else if (sub[0].equals("saveall")) {
            for (World world : Server.getInstance().getWorlds()) {
                for (MapleCharacter chr : world.getPlayerStorage().getAllCharacters()) {
                    chr.saveToDB(true);
                    System.out.println("[SQL] Saved "+chr.getName()+" to MySQL Database.");
                    chr.yellowMessage("Saved "+chr.getName()+" to MySQL Database.");
                }
            }
        } else if (sub[0].equals("npc")) {
            MapleNPC npc = MapleLifeFactory.getNPC(Integer.parseInt(sub[1]));
            if (npc != null) {
                npc.setPosition(player.getPosition());
                npc.setCy(player.getPosition().y);
                npc.setRx0(player.getPosition().x + 50);
                npc.setRx1(player.getPosition().x - 50);
                npc.setFh(player.getMap().getFootholds().findBelow(c.getPlayer().getPosition()).getId());
                player.getMap().addMapObject(npc);
                player.getMap().broadcastMessage(MaplePacketCreator.spawnNPC(npc));
            }
        } else if (sub[0].equals("jobperson")) {
            MapleCharacter victim = c.getChannelServer().getPlayerStorage().getCharacterByName(sub[1]);
            victim.changeJob(MapleJob.getById(Integer.parseInt(sub[2])));
            player.equipChanged();
        } else if (sub[0].equals("reloadwz")) {
        	//MapleLifeFactory life = new MapleLifeFactory();
        	MapleLifeFactory.stringDataWZ = MapleDataProviderFactory.getDataProvider(new File(System.getProperty("wzpath") + "/Mob.wz"));
        	//life.reloadDataProvider();
        } else if (sub[0].equals("pinkbean")) {
            player.getMap().spawnMonsterOnGroudBelow(MapleLifeFactory.getMonster(8820009), player.getPosition());
        } else if (sub[0].equals("playernpc")) {
            player.playerNPC(c.getChannelServer().getPlayerStorage().getCharacterByName(sub[1]), Integer.parseInt(sub[2]));
        } else if (sub[0].equals("setgmlevel")) {
            MapleCharacter victim = c.getChannelServer().getPlayerStorage().getCharacterByName(sub[1]);
            victim.setGM(Integer.parseInt(sub[2]));
            player.message("Done.");
            victim.getClient().disconnect();
        } else if (sub[0].equals("shutdown") || sub[0].equals("shutdownnow")) {
            int time = 60000;
            if (sub[0].equals("shutdownnow")) {
                time = 1;
            } else if (sub.length > 1) {
                time *= Integer.parseInt(sub[1]);
            }
            TimerManager.getInstance().schedule(Server.getInstance().shutdown(false), time);
        } else if (sub[0].equals("sql")) {
            final String query = Commands.joinStringFrom(sub, 1);
            try {
                PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(query);
                ps.executeUpdate();
                ps.close();
                player.message("Done " + query);
            } catch (SQLException e) {
                player.message("Query Failed: " + query);
            }
        } else if (sub[0].equals("sqlwithresult")) {
            String name = sub[1];
            final String query = Commands.joinStringFrom(sub, 2);
            try {
                PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(query);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    player.dropMessage(String.valueOf(rs.getObject(name)));
                }
                rs.close();
                ps.close();
            } catch (SQLException e) {
                player.message("Query Failed: " + query);
            }
        } else if (sub[0].equals("itemvac")) {
            List<MapleMapObject> items = player.getMap().getMapObjectsInRange(player.getPosition(), Double.POSITIVE_INFINITY, Arrays.asList(MapleMapObjectType.ITEM));
            for (MapleMapObject item : items) {
                    MapleMapItem mapitem = (MapleMapItem) item;

                    if (!MapleInventoryManipulator.addFromDrop(c, mapitem.getItem(), true)) {
                        continue;
                    }

                    //MapleInventoryManipulator.addFromDrop(c, mapitem.getItem(), true);
                    mapitem.setPickedUp(true);
                    player.getMap().broadcastMessage(MaplePacketCreator.removeItemFromMap(mapitem.getObjectId(), 2, player.getId()), mapitem.getPosition());
                    player.getMap().removeMapObject(item);
                    player.getMap().nullifyObject(item);
                    
                }
        } else if (sub[0].equals("dropinventory")) {
            if (sub.length < 2) {
            	player.yellowMessage("Syntax: !dropinventory all | equip | use | setup | etc | cash");
            }
            				
            String type = sub[1];
            if (type.equals("all")) {
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            //MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.EQUIP, (byte) i, tempItem.getQuantity(), false, true);
                            MapleInventoryManipulator.drop(c, MapleInventoryType.EQUIP, (byte) i, tempItem.getQuantity());
                    }
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.USE).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            //MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, (byte) i, tempItem.getQuantity(), false, true);
                            MapleInventoryManipulator.drop(c, MapleInventoryType.USE, (byte) i, tempItem.getQuantity());
                    }
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.ETC).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            //MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.ETC, (byte) i, tempItem.getQuantity(), false, true);
                            MapleInventoryManipulator.drop(c, MapleInventoryType.ETC, (byte) i, tempItem.getQuantity());
                    }
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.SETUP).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            //MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.SETUP, (byte) i, tempItem.getQuantity(), false, true);
                            MapleInventoryManipulator.drop(c, MapleInventoryType.SETUP, (byte) i, tempItem.getQuantity());
                    }
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.CASH).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            //MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.CASH, (byte) i, tempItem.getQuantity(), false, true);
                            MapleInventoryManipulator.drop(c, MapleInventoryType.CASH, (byte) i, tempItem.getQuantity());
                    }
                    player.message("Your whole inventory has been dropped.");
            }
            else if (type.equals("equip")) {
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            //MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.EQUIP, (byte) i, tempItem.getQuantity(), false, true);
                            MapleInventoryManipulator.drop(c, MapleInventoryType.EQUIP, (byte) i, tempItem.getQuantity());
                    }
                    player.message("Your EQUIP inventory has been dropped.");
            }
            else if (type.equals("use")) {
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.USE).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            //MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, (byte) i, tempItem.getQuantity(), false, true);
                            MapleInventoryManipulator.drop(c, MapleInventoryType.USE, (byte) i, tempItem.getQuantity());
                    }
                    player.message("Your USE inventory has been dropped.");
            }
            else if (type.equals("setup")) {
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.SETUP).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            //MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.SETUP, (byte) i, tempItem.getQuantity(), false, true);
                            MapleInventoryManipulator.drop(c, MapleInventoryType.SETUP, (byte) i, tempItem.getQuantity());
                    }
                    player.message("Your SETUP inventory has been dropped.");
            }
            else if (type.equals("etc")) {
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.ETC).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            //MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.ETC, (byte) i, tempItem.getQuantity(), false, true);
                            MapleInventoryManipulator.drop(c, MapleInventoryType.ETC, (byte) i, tempItem.getQuantity());
                    }
                    player.message("Your ETC inventory has been dropped.");
            }
            else if (type.equals("cash")) {
                    for (int i = 0; i < 101; i++) {
                            IItem tempItem = c.getPlayer().getInventory(MapleInventoryType.CASH).getItem((byte) i);
                            if (tempItem == null)
                                    continue;
                            //MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.CASH, (byte) i, tempItem.getQuantity(), false, true);
                            MapleInventoryManipulator.drop(c, MapleInventoryType.CASH, (byte) i, tempItem.getQuantity());
                    }
                    player.message("Your CASH inventory has been dropped.");
            }
            else {
            	player.message(type + " inventory does not exist!");
            }
        } else if (sub[0].equals("zakum")) {
            player.getMap().spawnFakeMonsterOnGroundBelow(MapleLifeFactory.getMonster(8800000), player.getPosition());
            for (int x = 8800003; x < 8800011; x++) {
                player.getMap().spawnMonsterOnGroudBelow(MapleLifeFactory.getMonster(x), player.getPosition());
            }
        } else {
            player.yellowMessage("Command " + heading + sub[0] + " does not exist.");
        }
    }

    private static String joinStringFrom(String arr[], int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < arr.length; i++) {
            builder.append(arr[i]);
            if (i != arr.length - 1) {
                builder.append(" ");
            }
        }
        return builder.toString();
    }
}