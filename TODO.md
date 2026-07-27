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
  * <s> Verify the teleport world, always remove car on reconnect and place back</s>
  * <s> Set GameMode</s>
  * Make sure the checkpoints keep updating
  * <s> Fix the LVL bug</s> <b>NOTE</b>: <i>should</i> be fixed
    
    * <s> → like when crossing the finish line: didn't end the race and didn't put him in spectator mode</s> 
* **Config** updates:
  * `racingGameMode`: race GameMode (default: Adventure)
  * <s> Rename `lapCount` to `defaultLapCount`</s> <b>UPDATE</b>: removed
  * `spectateOnFinish`
    * True = players get put to spectator when they finish the race
    * False = players can continue racing but won't count the laps (until `/race end`)
* Allow to convert legacy `config.yml` races to  new `races/[race].yml`
* Update RaceCreator to support auto-checkpoints
* Encourage people to use the new `PLANE` checkpoints
  * Easier to define
  * Work more reliably
* Make checkpoint alternate adding easier
* Add pit boxes