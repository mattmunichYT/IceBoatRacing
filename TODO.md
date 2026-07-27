### Todo
*Below you'll find a list of what I've planned to add/modify to this plugin*

* Send **ranking** on race end
  * With try/catching if there isn't enough data
* **Fix** the logout and back in during the race
  * Verify the teleport world, always remove car on reconnect and place back
  * Set GameMode
  * Make sure the checkpoints keep updating
  * Fix the LVL bug 
    * → like when crossing the finish line: didn't end the race and didn't put him in spectator mode 
* **Config** updates:
  * `racingGameMode`: race GameMode (default: Adventure)
  * Rename `lapCount` to `defaultLapCount`
  * `spectateOnFinish`
    * True = players get put to spectator when they finish the race
    * False = players can continue racing but won't count the laps (until `/race end`)
* Allow to convert legacy `config.yml` races to  new `races/[race].yml`
* Encourage people to use the new `PLANE` checkpoints
  * Easier to define
  * Work more reliably
* Make checkpoint alternate adding easier
* Add pit boxes