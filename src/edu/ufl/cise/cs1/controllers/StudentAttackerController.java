package edu.ufl.cise.cs1.controllers;
import game.controllers.AttackerController;
import game.models.*;
import java.awt.*;
import java.util.List;

public final class StudentAttackerController implements AttackerController
{
	public boolean anyTooClose(Attacker attacker, List<Defender> defenders, Node target) { // This function will tell me if any defenders are getting too close to a target node I choose... or at least, it used to be, but I changed it to just check if 3 out of 4 were too close, but kept the name.
		int attackerDistance = attacker.getLocation().getPathDistance(target); // Check how far our attacker is to the target node.
		int count = 0;
		for (Defender def : defenders) { // Iterate through our list of defenders to check the distance of each one.
			int defenderDistance = def.getLocation().getPathDistance(target); // Get the distance from the target node...
			if (defenderDistance < attackerDistance*3.25) { // .. and compare its distance to our attacker distance.
				/*
				The *3.25 above is to give us a little wiggle room, because we don't want a situation where we get there
				at EXACTLY the same time as a defender, so we check how close they are "a little early." 3.25 is a magic number
				chosen by randomly adjusting the value until my average score stopped going up.
				 */
				count++;
			}
		}
		if (count >= 3) { // If 3 defenders (or 4, if somehow two crossed the threshold in the same update) are closer to the target than you are, then this function returns true.
			// We don't want all 4 to be closer than the target we're checking, because it means we then cannot get to that target without dying (assuming the defenders go after us). We need an escape route!
			return true;
		} else return false; // If count does not reach 3, then return false, because that means at least 2 defenders are not blocking the path to the target.
	}

	public void init(Game game) { }

	public void shutdown(Game game) { }

	public int update(Game game,long timeDue)
	{
		int action = 0;
		Node target = null; // Just used for the debug path adding which I liked.
		boolean flee = false; // Also used for the debug path adding, if we're fleeing it'll be red instead of blue to indicate that our next direction was chosen out of fear.

		Attacker attacker = game.getAttacker(); // Storing our attacker in a variable up here so that everything is working off of the same exact attacker object.

		List<Integer> possibleDirs = attacker.getPossibleDirs(true); // From the sample. Gets the possible directions we can move.
		List<Node> powers = game.getPowerPillList(); // Store a list of our available power pills.
		List<Node> pills = game.getPillList(); // Store a list of our available little pills.
		List<Defender> defenders = game.getDefenders(); // And store a list of our defenders.

		Defender closestDefender = (Defender) attacker.getTargetActor(defenders, true); // Every update we wanna know who the closest defender to us is, as they will primarily decide our action.
		int distanceToClosestDefender = attacker.getLocation().getPathDistance(closestDefender.getLocation()); // Storing the distance between our attacker and the defender for use later.

		Node closestPower; // This will be the closest power pill, but we can't initialize it directly because sometimes it might be null.
		int distanceToClosestPower = 0; // Likewise with the node itself, this will be updated later.

		if (powers.size() > 0) { // We need to make sure there are still power pills left, which can be assumed if our list of power pills has any size at all.
			closestPower = attacker.getTargetNode(powers, true); // If there are pills left, overwrite our closestPower node with the closest power pill.
			distanceToClosestPower = attacker.getLocation().getPathDistance(closestPower); // And overwrite the distance, too, to be the distance between our attacker and the pill.
		} else closestPower = null; // If the list is empty, then set our closest power pill to "null."

		if (possibleDirs.size() != 0) { // I don't know a situation where possibleDirs WOULD be 0, but this is from the sample and I kept it in just in case of some weird edge case.
			action = attacker.getNextDir(attacker.getTargetNode(pills,true), true); // By default, we chase after the closest little pill.

			if (distanceToClosestDefender < 8 && closestDefender.isVulnerable() == false && closestDefender.getLairTime() == 0) { // If our closest defender is TOO close to us, and it's not in a vulnerable state, and it's not in the lair, then we need to act accordingly.
				// This is the first if-statement because above all else, we want to make sure the defenders NEVER get too close, if possible. This is prioritized above power pills and normal pills and eating vulnerable defenders.
				// 8 was chosen again mostly at random and tweaked until my score stopped going up.
				if (distanceToClosestPower != 0 && distanceToClosestPower < distanceToClosestDefender) { // This will hardly ever be true, but if somehow the defender is too close, but a power pill is closer...
					action = attacker.getNextDir(closestPower, true); // ... then we want to go after the power pill to save ourselves.
				} else { // If the nearest power pill is not closer than the defender, then we just want to flee.
					int defenderDirection = attacker.getNextDir(closestDefender.getLocation(), true); // ... but first, let's see which direction the defender we're fleeing from is.
					if (attacker.getNextDir(attacker.getTargetNode(pills,true), true) != defenderDirection) {
						/*
						If the direction the defender is in is NOT the same direction we'd go to get the nearest little pill,
						then we can actually prioritize going after the normal pill first over just blindly fleeing until we're safe.
						This last minute added check alone brought my average score up from 8060 to 10650. Wow!
						 */
						action = attacker.getNextDir(attacker.getTargetNode(pills,true), true);
					} else action = attacker.getNextDir(closestDefender.getLocation(), false); // If going to the next little pill would get us killed, though, just blindly flee.
				}
				flee = true; // Set flee to true if the defender is too close, for our debug pathway code later, since all of the above decisions are made out of FEAR.
			} else if (closestDefender.isVulnerable() == true && closestDefender.getVulnerableTime() > 5 && distanceToClosestDefender < 64) {
				/*
				If the closest defender is not TOO close, then we can relax and do other things. Priority #2 (after FEAR) is eating vulnerable defenders.
				However, we need to make sure it has at least 5 seconds(?) of vulnerability left and is not further than 64 distance away (chosen at random until score stopped going up etc etc)
				or else we might chase after it, only for it to STOP being vulnerable once we get close and then immediately turn and kill us.
				*/
				target = closestDefender.getLocation(); // Set our target to the yummy defender.
				action = attacker.getNextDir(target, true); // Choose our next direction to have us approach the target.
			} else if (distanceToClosestPower != 0 && anyTooClose(attacker, defenders, closestPower) == true) { // If there is no close enough vulnerable defender, then let's go eat a power pill.
				/*
				This is where our anyTooClose function comes in handy, because by passing in the closest power pill node as the target, we can check
				if too many (in our case 3) defenders are blocking our path to the power pill. If 3 defenders are blocking it, then we need to
				get to the power pill immediately before we get completely blocked off from eating it, so that is Priority #3.
				*/
				target = closestPower; // Set target to the closest power pill.
				action = attacker.getNextDir(closestPower, true); // Move in the direction approaching the power pill.
			}

			/*
			If none of the above if-statements get acted on, then our attacker can relax a little and just keep at our default
			action, which is eating the closest normal pill, and not worry about defenders or power pills for now.
			*/
		}
		else
			action = -1; // From the sample. If we have no possible directions to go somehow, then... do nothing?


		if (flee == false) { // If we're not fleeing, our target will be pathed in blue, to show what we're after.
			attacker.addPathTo(game, Color.BLUE, target); // This doesn't get run if we don't have a target though i.e. if we're just doing the default action of eating normal pills.
		} else { // If we ARE fleeing, then we're fleeing from the closest defender, so lets add a red path to the defender to show that we are currently choosing actions specifically to avoid it.
			attacker.addPathTo(game, Color.RED, closestDefender.getLocation());
		}

		return action; // Return our action variable, whatever it turned out to be, as our next move.
	}
}