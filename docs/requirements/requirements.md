# Requirements

## Technology (mostly still to be installed)
Kotlin
Android Studio
gh
JetBrains Claude Code Plugin
Frameworks?
Libraries?

## Datasets
* Greek Audio - Greek Words
* English Audio - Greek Words
* Greek Word Frequency
* Phrasebooks
	* Letter sounds
	* Getting Directions
	* Airport
	* Resturant
	* Store
	* Sourviners
	* Cruise
	* Famous locations
* User History
	* Words Played
		* Repetitions
		* Success Rate
		* FIFO buffer of last 4 success/fail flags.
		* last played
	* Letters Played
		* Repetitions
		* Success Rate
		* FIFO buffer of last 4 success/fail flags.
		* last played
		
What open source datasets are available, and what liscences will that push us towards?  I'd prefer actual native speaker audio instead of AI generated audio.  I'd prefer an open source dataset for translation as well.

## Play Items Selection

Need to hammer out the exact rules of spaced repition.  Below is a beginning of a draft

* When repitions and sucess rates are the same, increase probability of selecting:
  * words with higher frequency in the language 
  * words included in the currently selected phrase book
  * shorter words
* When success rates are low, increase the probability of selecting the word.
* When repition is high and success rate is high, reduce the probability of selecting the word.
* Only 5 unfamiliar words should be in the same level.  An unfamiliar word has fewer than 10 repitions or less than 75% overall success rate, or less than 100% recent success rate.
* The number of duplicates in a level is high when prior repitions or success rate is low.  Level 1 may just have the same word 10 times in a row to establish one learned word.

## Game Play

* The engine is requested play items.
* play items begin being populated at the top of the screen, displaying the text word.
* play items descend down the screen until they reach the bottom and then dissapear.
* a visible play item has it's audio data played aloud.
* The user responds to the audio by tapping the word.
* Tapping the wrong word is a failure.
* Not tapping before the word reaches the bottom of the screen is a failure.
* failure will trigger a red starburst animation.  If the word fell off screen the starburst origionates there, otherwise it origionates at the tap location.
* Tappng the right word is a success.
* success will triger a green starburst animation, which origionates at the tap location.
* After a success or failure by the word reaching the bottom, a new word on screen will have it's audio played aloud (repeat)
* difficulty settings will affect the number of words per level, number of distinct play items per level (may repeat to get full number of words per level) number of words on screen at once, and speed the words move.
* difficulty will increase when success of the last level is above 90%