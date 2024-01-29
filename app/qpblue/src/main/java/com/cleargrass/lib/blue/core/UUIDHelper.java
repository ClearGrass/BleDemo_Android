package com.cleargrass.lib.blue.core;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UUIDHelper {


	// base UUID used to build 128 bit Bluetooth UUIDs
	public static final String UUID_BASE = "0000XXXX-0000-1000-8000-00805f9b34fb";

	// handle 16 and 128 bit UUIDs
	public static UUID uuidFromString(String uuid) {

		if (uuid.length() == 4) {
			uuid = UUID_BASE.replace("XXXX", uuid);
		}
		return UUID.fromString(uuid);
	}

	// return 16 bit UUIDs where possible
	public static String uuidToString(UUID uuid) {
		String longUUID = uuid.toString();
		Pattern pattern = Pattern.compile("0000(.{4})-0000-1000-8000-00805f9b34fb", Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(longUUID);
		if (matcher.matches()) {
			// 16 bit UUID
			return matcher.group(1);
		} else {
			return longUUID;
		}
	}

	/**
	 * 简化显示UUID （仅对特殊UUID起效）
	 * @param uuid
	 * @return
	 */
    @NotNull
    public static String simpler(@NotNull UUID uuid) {
        String uuidS = uuid.toString();
		if (uuidS.endsWith("0000-1000-8000-00805f9b34fb")) {
			uuidS = uuidS.replace("-0000-1000-8000-00805f9b34fb", "");
			if (uuidS.startsWith("0000")) {
				uuidS = uuidS.substring(4);
			}
		}
		return uuidS;
    }
	/**
	 * 简化显示UUID
	 * force 为 true时，仅显示前8个或前4个字符，通常只用于显示，不用于比较两个uuid
	 * @param uuid
	 * @return
	 */
	@NotNull
	public static String simpler(@NotNull UUID uuid, boolean force) {
		if (!force) {
			return simpler(uuid);
		}
		String uuidS = uuid.toString().substring(0, 8);
		if (uuidS.startsWith("0000")) {
			uuidS = uuidS.substring(4);
		}
		return uuidS;
	}
}