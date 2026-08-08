### To-do / Planning
*Below you'll find a list of what I've planned on adding or modifying.*

---

**Note:**
* <s>[TODO]</s> is completed
* Unless there is something more written next to it
  * <s> Example</s> <b>UPDATE</b>: Some example   
---
* <s> Send **ranking** on race end</s>
  
  * <s> With try/catching if there isn't enough data </s>
* **Fix** the logout and back in during the race
  * <s> End race if last racer logs out</s>
  * <s> Verify the teleport world, always remove car on reconnect and place back</s>
  * <s> Set GameMode</s>
  * Make sure the checkpoints keep updating
  * <s> Fix the LVL bug</s> <b>NOTE</b>: <i>should</i> be fixed
  
    * <s> → like when crossing the finish line: didn't end the race and didn't put him in spectator mode</s> 
* **Config** updates: <br/>
  * <s> `racingGameMode`: race GameMode (default: Adventure) </s>
  * <s> Rename `lapCount` to `defaultLapCount`</s> <b>UPDATE</b>: removed
  * <s> `spectateOnFinish` </s><br/>
    * <s> True = players get put to spectator when they finish the race</s><br/>
    * <s> False = players can continue racing but won't count the laps (until `/race end`) </s>
  * Per race startRotation
* Allow to convert legacy `config.yml` races to  new `races/[race].yml` 
	* (Planned, but people will have to redefine checks anyway, soo not so sure it will be done)
* <s> Update RaceCreator to support auto-checkpoints </s>
* Encourage people to use the new `PLANE` checkpoints
  * Easier to define
  * Work more reliably
	* -> Basically fix all bugs, warn on old races (with BOX checkpoints -> deprecated)
* <s> Checkpoint autotrace resize not working </s>
* <s> Prevent from creating 2 cars with same owner </s>
* <s> Remove prefix for best sector times in per-player ranking </s>
* Test all the new features (est. 65-70% done)
* Make checkpoint alternate adding easier (Planned pretty soon)
	* <s> Add a way to get a checkpoint's ID</s> 
	* <s> Add a way to view checkpoints</s>
	* Add IDs to the view ?
* Add training system (base system maybe ~ same time as pit boxes)
	* `/race train <race>`
	* Allows to train solo on a race
	* Multiple players at the same time
	* Customize number of allowed trainings (pit boxes will be done before this ^^')
		* Infinite, 1/day,... (as permissions like `ibr(.race?).train.(bypass/limited)`?)
		* Add more with command
* Add **pit boxes** (for v2.1 or v3)
	* Either use /IceBoatRacing/races/[race]/pitboxes/[one file for each pit box].yml or /IceBoatRacing/races/[race].yml (prefer 1st, as it can get hard to find when a lot in 1 file)
	* Multiple types of 'tasks':
		* Time (like wait a certain amount of seconds, to be customizable for each pit box 
		* Idk like random creative things that take some time but you can train to spend less time at the pit box 
	* Creation:
		* Name?
		* Define loc where the player should go to trigger
		* Define task
		* Define who can use (player names or '*' for everyone)
	* Either slow the boat down a lot or just cancel MoveEvent

