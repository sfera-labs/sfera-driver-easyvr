package cc.sferalabs.sfera.drivers.easyvr;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.slf4j.Logger;

import cc.sferalabs.sfera.drivers.easyvr.events.RecogSDEvent;
import cc.sferalabs.sfera.drivers.easyvr.events.RecogSIEvent;
import cc.sferalabs.sfera.events.Bus;
import cc.sferalabs.sfera.io.comm.CommPort;
import cc.sferalabs.sfera.io.comm.CommPortException;
import cc.sferalabs.sfera.io.comm.CommPortListener;
import cc.sferalabs.sfera.io.comm.CommPortTimeoutException;

public class CommunicationHandler implements CommPortListener {

	private static final byte[] SPACE_BYTES = { ' ' };
	private static final int MODE_UNDEF = -1;
	private static final int MODE_RECOG_SI = 1;
	private static final int MODE_RECOG_SD = 2;
	private static final int ASYN_READ_STATE_UNDEF = -1;
	private static final int ASYN_READ_STATE_SIMILAR = 1;
	private static final int ASYN_READ_STATE_RESULT = 2;
	private static final int ASYN_READ_STATE_ERROR0 = 3;
	private static final int ASYN_READ_STATE_ERROR1 = 4;

	private final EasyVR driver;
	private final CommPort commPort;
	private final Logger logger;
	private int recogTimeout = -1;
	private int mode = MODE_UNDEF;
	private int asynReadState = ASYN_READ_STATE_UNDEF;
	private int error = 0;
	private int index = -1;

	/**
	 * 
	 * @param EasyVR driver
	 * @param commPort
	 * @param countryCode
	 * @param logger
	 */
	CommunicationHandler(EasyVR driver, CommPort commPort, Logger logger) {
		this.driver = driver;
		this.commPort = commPort;
		this.logger = logger;
	}

	@Override
	public void onRead(byte[] bytes) {

		for (byte b : bytes) {
			try {
				commPort.writeBytes(SPACE_BYTES);
				switch (mode) {
				case MODE_RECOG_SI:
					switch (asynReadState) {
					case ASYN_READ_STATE_UNDEF:
						if (b == 's') {
							asynReadState = ASYN_READ_STATE_SIMILAR;
						} else if (b == 'e') {
							asynReadState = ASYN_READ_STATE_ERROR0;
						} else {
							commPort.removeListener();
							mode = MODE_UNDEF;
							Bus.post(new RecogSIEvent(driver, index, -1));
						}
						break;
					case ASYN_READ_STATE_SIMILAR:
						commPort.removeListener();
						mode = MODE_UNDEF;
						asynReadState = ASYN_READ_STATE_UNDEF;
						Bus.post(new RecogSIEvent(driver, index, decodeArg(b)));
						break;
					case ASYN_READ_STATE_ERROR0:
						error = 16 * decodeArg(b);
						asynReadState = ASYN_READ_STATE_ERROR1;
						break;
					case ASYN_READ_STATE_ERROR1:
						commPort.removeListener();
						mode = MODE_UNDEF;
						asynReadState = ASYN_READ_STATE_UNDEF;
						Bus.post(new RecogSIEvent(driver, index,
								-(error + decodeArg(b))));
						break;
					}
					break;
				case MODE_RECOG_SD:
					switch (asynReadState) {
					case ASYN_READ_STATE_UNDEF:
						if (b == 'r') {
							asynReadState = ASYN_READ_STATE_RESULT;
						} else if (b == 'e') {
							asynReadState = ASYN_READ_STATE_ERROR0;
						} else {
							commPort.removeListener();
							mode = MODE_UNDEF;
							Bus.post(new RecogSDEvent(driver, index, -1));
						}
						break;
					case ASYN_READ_STATE_RESULT:
						commPort.removeListener();
						mode = MODE_UNDEF;
						asynReadState = ASYN_READ_STATE_UNDEF;
						Bus.post(new RecogSDEvent(driver, index, decodeArg(b)));
						break;
					case ASYN_READ_STATE_ERROR0:
						error = 16 * decodeArg(b);
						asynReadState = ASYN_READ_STATE_ERROR1;
						break;
					case ASYN_READ_STATE_ERROR1:
						commPort.removeListener();
						mode = MODE_UNDEF;
						asynReadState = ASYN_READ_STATE_UNDEF;
						Bus.post(new RecogSDEvent(driver, index,
								-(error + decodeArg(b))));
						break;
					}
					break;
				}
			} catch (CommPortException e) {
				logger.error("Error reading from comm port", e);
				driver.quit();
			}
		}
	}

	@Override
	public void onError(Throwable t) {

		logger.error("Error reading from comm port", t);
		driver.quit();
	}

	synchronized boolean commandAddSD(int group, int position, String label) {
		final byte[] cmd_group_sd = { 'g', encodeArg(group),
				encodeArg(position) };
		final byte[] cmd_name_sd = { 'n', encodeArg(group),
				encodeArg(position), 0 };

		try {
			commPort.clear();
			commPort.writeBytes(cmd_group_sd);
			if (readBytes(1)[0] != 'o') {
				return false;
			}
			byte[] lbytes = encodeLabel(label);
			cmd_name_sd[3] = encodeArg(lbytes.length);
			commPort.writeBytes(cmd_name_sd);
			commPort.writeBytes(lbytes);
			if (readBytes(1)[0] != 'o') {
				return false;
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	synchronized boolean commandBreak() {
		final byte[] cmd_break = { 'b' };

		try {
			commPort.removeListener();
		} catch (CommPortException e) {
		}

		try {
			mode = MODE_UNDEF;
			for (int i = 0; i < 100; i++) {
				commPort.clear();
				commPort.writeBytes(cmd_break);
				try {
					if (readBytes(1)[0] == 'o') {
						return true;
					}
				} catch (CommPortTimeoutException e) {
				}
			}
		} catch (Exception e) {
		}
		return false;
	}

	synchronized int commandCountSD(int group) {
		final byte[] cmd_count_sd = { 'c', encodeArg(group) };

		try {
			commPort.clear();
			commPort.writeBytes(cmd_count_sd);
			byte[] resa = readBytes(2);
			if (resa[0] == cmd_count_sd[0]) {
				int count = decodeArg(resa[1]);
				return (count == -1) ? 32 : count;
			}
		} catch (Exception e) {
		}
		return -1;
	}

	synchronized String commandDumpSD(int group, int position) {
		final byte[] cmd_dump_sd = { 'p', encodeArg(group), encodeArg(position) };
		byte[] resa, labela;

		try {
			commPort.clear();
			commPort.writeBytes(cmd_dump_sd);
			resa = readBytes(4);
			if (resa[0] != 'd') {
				return null;
			}
			int len = decodeArg(resa[3]);
			labela = readBytes(len);
			if (len == labela.length) {
				return decodeLabel(decodeArg(resa[1]) + " "
						+ decodeArg(resa[2]) + " "
						+ new String(labela, StandardCharsets.US_ASCII));
			} else {
				return null;
			}
		} catch (Exception e) {
			return null;
		}
	}

	synchronized boolean commandEraseSD(int group, int position) {
		final byte[] cmd_erase_sd = { 'e', encodeArg(group),
				encodeArg(position) };

		try {
			commPort.clear();
			commPort.writeBytes(cmd_erase_sd);
			if (readBytes(1)[0] == 'o') {
				return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			return false;
		}
	}

	synchronized int commandID() {
		final byte[] cmd_id = { 'x' };

		try {
			commPort.clear();
			commPort.writeBytes(cmd_id);
			byte[] resa = readBytes(2);
			if (resa[0] == cmd_id[0]) {
				return decodeArg(resa[1]);
			}
		} catch (Exception e) {
		}
		return -1;
	}

	synchronized boolean commandLanguageSI(int language) {
		final byte[] cmd_language = { 'l', encodeArg(language) };

		try {
			commPort.clear();
			commPort.writeBytes(cmd_language);
			if (readBytes(1)[0] == 'o') {
				return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			return false;
		}
	}

	synchronized boolean commandLevelSD(int level) {
		final byte[] cmd_level = { 'v', encodeArg(level) };

		try {
			commPort.clear();
			commPort.writeBytes(cmd_level);
			if (readBytes(1)[0] == 'o') {
				return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			return false;
		}
	}

	synchronized boolean commandLevelSI(int level) {
		final byte[] cmd_knob = { 'k', encodeArg(level) };

		try {
			commPort.clear();
			commPort.writeBytes(cmd_knob);
			if (readBytes(1)[0] == 'o') {
				return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			return false;
		}
	}

	synchronized boolean commandMicDist(int distance) {
		final byte[] cmd_mic_dist = { 'k', '@', encodeArg(distance) };

		try {
			commPort.clear();
			commPort.writeBytes(cmd_mic_dist);
			if (readBytes(1)[0] == 'o') {
				return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			return false;
		}
	}

	synchronized boolean commandPlayDTMF(int index, int duration) {
		final byte[] cmd_play_dtmf = { 'w', '@', encodeArg(index),
				encodeArg(duration) };

		try {
			commPort.clear();
			commPort.writeBytes(cmd_play_dtmf);
			if (readBytes(1, EasyVR.RESPONSE_LONG_TIMEOUT)[0] == 'o') {
				return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			return false;
		}
	}

	synchronized boolean commandPlaySX(int index, int volume) {
		final byte[] cmd_play_sx = { 'w', encodeArg(index / 32),
				encodeArg(index % 32), encodeArg(volume) };

		try {
			commPort.clear();
			commPort.writeBytes(cmd_play_sx);
			if (readBytes(1, EasyVR.RESPONSE_LONG_TIMEOUT)[0] == 'o') {
				return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			return false;
		}
	}

	synchronized boolean commandRecogSD(int group) {
		final byte[] cmd_recog_sd = { 'd', encodeArg(group) };

		if (mode != MODE_UNDEF) {
			commandBreak();
		}
		try {
			commPort.clear();
			commPort.setListener(this);
			commPort.writeBytes(cmd_recog_sd);
			mode = MODE_RECOG_SD;
			index = group;
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	synchronized boolean commandRecogSI(int group) {
		final byte[] cmd_recog_si = { 'i', encodeArg(group) };

		if (mode != MODE_UNDEF) {
			commandBreak();
		}
		try {
			commPort.clear();
			commPort.setListener(this);
			commPort.writeBytes(cmd_recog_si);
			mode = MODE_RECOG_SI;
			index = group;
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	synchronized boolean commandRemoveSD(int group, int position) {
		final byte[] cmd_ungroup_sd = { 'u', encodeArg(group),
				encodeArg(position) };

		try {
			commPort.clear();
			commPort.writeBytes(cmd_ungroup_sd);
			if (readBytes(1)[0] != 'o') {
				return false;
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	synchronized boolean commandResetAll() {
		final byte[] cmd_resetall = { 'r', 'R' };

		try {
			commPort.clear();
			commPort.writeBytes(cmd_resetall);
			if (readBytes(1)[0] == 'o') {
				return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			return false;
		}
	}

	synchronized boolean commandTimeout(int seconds) {
		final byte[] cmd_timeout = { 'o', encodeArg(seconds) };

		if (recogTimeout != seconds) {
			try {
				if (mode != MODE_UNDEF) {
					commandBreak();
				}
				commPort.clear();
				commPort.writeBytes(cmd_timeout);
				if (readBytes(1)[0] == 'o') {
					recogTimeout = seconds;
					return true;
				} else {
					return false;
				}
			} catch (Exception e) {
				return false;
			}
		} else {
			return true;
		}
	}

	synchronized String commandTrainSD(int group, int position) {
		final byte[] cmd_train_sd = { 't', encodeArg(group),
				encodeArg(position) };

		try {
			commPort.clear();
			commPort.writeBytes(cmd_train_sd);
			switch (readBytes(1, 5000)[0]) { // training timeout is 3 seconds,
												// we wait up tu 5 seconds
			case 'o':
				return "trained";
			case 'r':
				return ("similar_to_sd " + decodeArg(readBytes(1)[0]));
			case 's':
				return ("similar_to_si " + decodeArg(readBytes(1)[0]));
			case 'e':
				byte[] resa = readBytes(2);
				return ("error " + decodeArg((byte) (resa[0] * 16 + resa[1])));
			}
		} catch (Exception e) {
		}
		return null;
	}

	synchronized boolean commandTransmitDelay(int time) {
		final byte[] cmd_timeout = { 'y', encodeArg(time) };

		try {
			commPort.clear();
			commPort.writeBytes(cmd_timeout);
			if (readBytes(1)[0] == 'o') {
				return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			return false;
		}
	}

	private byte[] readBytes(int len) throws CommPortException,
			CommPortTimeoutException {

		return readBytes(len, EasyVR.RESPONSE_TIMEOUT);
	}

	private byte[] readBytes(int len, int timeoutMillis)
			throws CommPortException, CommPortTimeoutException {
		byte[] result = new byte[len];
		int count = -1;

		for (int i = 0; i < len; i++) {
			if (commPort.readBytes(result, i, 1, timeoutMillis) == 1) {
				commPort.writeBytes(SPACE_BYTES);
			} else {
				count = i;
			}
		}
		return (count == -1) ? result : Arrays.copyOf(result, count);
	}

	private static Integer decodeArg(Byte b) {

		return (b != null) ? b - 'A' : null;
	}

	private static String decodeLabel(String data) {
		StringBuffer sb = new StringBuffer();

		for (int i = 0; i < data.length(); i++) {
			char c = data.charAt(i);
			if (c == '^') {
				sb.append(data.charAt(++i) - 'A');
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private static byte encodeArg(int i) {

		return (byte) (i + 'A');
	}

	private static byte[] encodeLabel(String label) {
		StringBuffer sb = new StringBuffer();

		for (int i = 0; i < label.length(); i++) {
			char c = label.charAt(i);
			if (c >= '0' && c <= '9') {
				sb.append('^');
				sb.append((char) (c - '0' + 'A'));
			} else if (c >= 'A' && c <= 0x60) {
				sb.append(c);
			}
		}
		return sb.toString().getBytes(StandardCharsets.US_ASCII);
	}
}
