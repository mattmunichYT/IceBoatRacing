### To-do / Planning
*Below you'll find a list of what I've planned on adding or modifying.*

---

**Note:**
* <s>[TODO]</s> is completed
* Unless there is something more written next to it
  * <s> Example</s> <b>UPDATE</b>: Some example   
---
* **Config** updates: <br/>
  * Per race startRotation
* Allow to convert legacy `config.yml` races to  new `races/[race].yml` 
	* (Planned, but people will have to redefine checks anyway, soo not so sure it will be done)
* Encourage people to use the new `PLANE` checkpoints
  * Easier to define
  * Work more reliably
	* → Basically fix all bugs, warn on old races (with BOX checkpoints -> deprecated)
* Test all the new features (est. 90-95% done)
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

