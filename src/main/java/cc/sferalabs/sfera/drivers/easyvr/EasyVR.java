package cc.sferalabs.sfera.drivers.easyvr;

import cc.sferalabs.sfera.drivers.Driver;
import cc.sferalabs.sfera.drivers.easyvr.events.ConnectionEvent;
import cc.sferalabs.sfera.drivers.easyvr.events.RecogSDEvent;
import cc.sferalabs.sfera.events.Bus;
import cc.sferalabs.sfera.io.comm.CommPort;
import cc.sferalabs.sfera.io.comm.CommPortException;
import cc.sferalabs.sfera.core.Configuration;

/**
 * EasyVR 3 driver.
 * <p>
 * EasyVR 3 is a multi-purpose speech recognition module manufactured by
 * RoboTech srl. This driver implements a substantial subset of the EasyVR 3
 * commands as specified in the User Manual version 1.0.11 and has been tested
 * with EasyVR 3 firmware version 8.
 * 
 * @sfera.config serial_port
 *            mandatory serial port name
 * @sfera.config baud_rate
 *            optional baud rate (9600-115200). Default is 9600
 * @sfera.config level
 *            the strictness level to use for recognition of speaker-dependent
 *            custom commands (1-5): 1 = easy; 2 = average (default); 5 = hard.
 *            A higher setting will result in more recognition errors
 * @sfera.config knob
 *            the confidence threshold to use for recognition of
 *            speaker-independent built-in words (0-4): 0 = loosest, more valid
 *            results; 2 = typical value (default); 4 = tightest, fewer valid
 *            results. Note that knob level is ignored for trigger words
 * @sfera.config language
 *            the language to use for recognition of built-in words: 0 = English
 *            (default); 1 = Italian; 2 = Japanese; 3 = German; 4 = Spanish; 5 =
 *            French
 * 
 * @author Ulderico Arcidiaco
 *
 * @version 1.0.0
 *
 * @see <a href="http://www.veear.eu/products/easyvr3/">EasyVR 3 product
 *      page</a>
 */
public class EasyVR extends Driver {

	static final int RESPONSE_TIMEOUT = 2000;
	static final int RESPONSE_LONG_TIMEOUT = 60000;

	private CommPort commPort;
	private CommunicationHandler commHandler;

	public EasyVR(String id) {
		super(id);
	}

	@Override
	protected boolean onInit(Configuration config) throws InterruptedException {

		String portName = config.get("serial_port", null);
		if (portName == null) {
			log.error("Serial port not set");
			return false;
		}

		try {
			commPort = CommPort.open(portName);
			int baudRate = config.get("baud_rate", 9600);
			commPort.setParams(baudRate, 8, 1, CommPort.PARITY_NONE,
					CommPort.FLOWCONTROL_NONE);
			commHandler = new CommunicationHandler(this, commPort, log);
			Thread.sleep(1000); // wait 1 second before sending first command
			if (!commHandler.commandBreak()) {
				log.error("Error initializing EasyVR: handshake error");
				return false;
			}
			if (!commHandler.commandTransmitDelay(0)) {
				log.error("Error initializing EasyVR: transmit delay setting error");
				return false;
			}
			if (commHandler.commandID() < 2) {
				log.error("Error initializing EasyVR: device ID mismatch");
				return false;
			}
			if (!commHandler.commandTimeout(0)) {
				log.error("Error initializing EasyVR: timeout setting error");
				return false;
			}
			if (!commHandler.commandLevelSD(config.get("level", 2))) {
				log.error("Error initializing EasyVR: SD strictness level setting error");
				return false;
			}
			if (!commHandler.commandLevelSI(config.get("knob", 2))) {
				log.error("Error initializing EasyVR: SI strictness level setting error");
				return false;
			}
			if (!commHandler.commandLanguageSI(config.get("language", 0))) {
				log.error("Error initializing EasyVR: SI language setting error");
				return false;
			}
			Bus.postIfChanged(new ConnectionEvent(this, true));
			return true;
		} catch (CommPortException e) {
			log.error("Error initializing serial port", e);
			return false;
		}
	}

	@Override
	protected boolean loop() throws InterruptedException {

		Thread.sleep(1000);
		return true;
	}

	@Override
	protected void onQuit() {

		Bus.postIfChanged(new ConnectionEvent(this, false));
		try {
			commPort.close();
		} catch (Exception e) {
		}
	}

	/**
	 * Adds a new speaker-dependent custom command to a group.
	 * 
	 * @param group
	 *            the group index (0 = trigger, 1-15 = generic, 16 = password)
	 * @param position
	 *            the command position (0-31)
	 * @param label
	 *            the text for the command's label (ASCII characters from 'A' to
	 *            '`' and digits)
	 * @return {@code true} if successful
	 */
	public boolean addCommand(int group, int position, String label) {

		return commHandler.commandAddSD(group, position, label);
	}

	/**
	 * Interrupts pending recognition or playback operations.
	 */
	public void stop() {

		commHandler.commandBreak();
	}

	/**
	 * Erases the training data of a speaker-dependent custom command.
	 * 
	 * @param group
	 *            the group index (0 = trigger, 1-15 = generic, 16 = password)
	 * @param position
	 *            the command position (0-31)
	 * @return {@code true} if successful
	 */
	public boolean eraseCommand(int group, int position) {

		return commHandler.commandEraseSD(group, position);
	}

	/**
	 * Retrieves the name and training data of a custom command.
	 * 
	 * @param group
	 *            the group index (0 = trigger, 1-15 = generic, 16 = password)
	 * @param position
	 *            the command position (0-31)
	 * @return a {@code String} with three items, separated with space, or null
	 *         in case of errors:<br>
	 *         - training information (-1=empty, 1-6 = training count, +8 =
	 *         SD/SV conflict, +16 = SI conflict)<br>
	 *         - conflicting command position (0-31, only meaningful when
	 *         trained) - text of label
	 */
	public String dumpCommand(int group, int position) {

		return commHandler.commandDumpSD(group, position);
	}

	/**
	 * Gets the number of custom commands in the specified group.
	 * 
	 * @param group
	 *            the group index (0 = trigger, 1-15 = generic, 16 = password)
	 * @return the number of commands; -1 in case of errors
	 */
	public int getCommandCount(int group) {

		return commHandler.commandCountSD(group);
	}

	/**
	 * Starts recognition of a speaker-dependent custom command and generates a
	 * {@link RecogSDEvent} event.
	 * 
	 * @param group
	 *            the group index (0 = trigger, 1-15 = generic, 16 = password)
	 * @return {@code true} if successful
	 */
	public boolean recognizeCommand(int group) {

		return commHandler.commandRecogSD(group);
	}

	/**
	 * Starts recognition of a speaker-independent word and generates a
	 * {@link RecogSIEvent} event.
	 * 
	 * @param group
	 *            the group index (0 = trigger, 1-15 = generic, 16 = password)
	 * @return {@code true} if successful
	 */
	public boolean recognizeWord(int group) {

		return commHandler.commandRecogSI(group);
	}

	/**
	 * Plays a phone tone.
	 * 
	 * @param tone
	 *            the index of the tone (0-9 for digits, 10 for '*' key, 11 for
	 *            '#' key and 12-15 for extra keys 'A' to 'D', -1 for the dial
	 *            tone)
	 * @param duration
	 *            (1-32) is the tone duration in 40 milliseconds units, or in
	 *            seconds for the dial tone
	 * @return {@code true} if successful
	 */
	public boolean playPhoneTone(int tone, int duration) {

		return commHandler.commandPlayDTMF(tone, duration);
	}

	/**
	 * Plays a sound from the sound table.
	 * 
	 * @param index
	 *            the index of the target sound in the sound table (1-1023), or
	 *            0 for built-in beep
	 * @param volume
	 *            playback volume (0-31, 0 = min volume, 15 = full scale, 31 =
	 *            double gain)
	 * @return {@code true} if successful
	 */
	public boolean playSound(int index, int volume) {

		return commHandler.commandPlaySX(index, volume);
	}

	/**
	 * Removes a speaker-dependent custom command from a group.
	 * 
	 * @param group
	 *            the group index (0 = trigger, 1-15 = generic, 16 = password)
	 * @param position
	 *            the command position (0-31)
	 * @return {@code true} if successful
	 */
	public boolean removeCommand(int group, int position) {

		return commHandler.commandRemoveSD(group, position);
	}

	/**
	 * Sets the language to use for recognition of built-in words.
	 * 
	 * @param language
	 *            the language: 0 = English (default); 1 = Italian; 2 =
	 *            Japanese; 3 = German; 4 = Spanish; 5 = French
	 * @return {@code true} if successful
	 */
	public boolean setLanguage(int language) {

		return commHandler.commandLanguageSI(language);
	}

	/**
	 * Sets the strictness level to use for recognition of speaker-dependent
	 * custom commands.
	 * 
	 * @param level
	 *            the strictness level (1-5): 1 = easy; 2 = average (default); 5
	 *            = hard. A higher setting will result in more recognition
	 *            errors.
	 * @return {@code true} if successful
	 */
	public boolean setLevel(int level) {

		return commHandler.commandLevelSD(level);
	}

	/**
	 * Sets the confidence threshold to use for recognition of
	 * speaker-independent built-in words.
	 * 
	 * @param level
	 *            the confidence threshold (0-4): 0 = loosest, more valid
	 *            results; 2 = typical value (default); 4 = tightest, fewer
	 *            valid results. Note that knob level is ignored for trigger
	 *            words.
	 * @return {@code true} if successful
	 */
	public boolean setKnob(int level) {

		return commHandler.commandLevelSI(level);
	}

	/**
	 * Sets the timeout to use for any recognition task.
	 * 
	 * @param seconds
	 *            the maximum time the module keeps listening for a word or a
	 *            command (0-31): 0 = infinite (default); 1-31 = seconds
	 * @return {@code true} if successful
	 */
	public boolean setTimeout(int seconds) {

		return commHandler.commandTimeout(seconds);
	}

	/**
	 * Starts training of a custom command.
	 * 
	 * @param group
	 *            the group index (0 = trigger, 1-15 = generic, 16 = password)
	 * @param position
	 *            the command position (0-31)
	 * @return {@code true} if successful
	 */
	public String trainCommand(int group, int position) {

		return commHandler.commandTrainSD(group, position);
	}
}
