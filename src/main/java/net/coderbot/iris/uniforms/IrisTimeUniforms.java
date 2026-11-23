package net.coderbot.iris.uniforms;

import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.coderbot.iris.gl.uniform.UniformUpdateFrequency;
import net.coderbot.iris.vendored.joml.Vector2i;
import net.coderbot.iris.vendored.joml.Vector3i;

import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalField;
import java.util.GregorianCalendar;

public class IrisTimeUniforms {
	private static LocalDateTime dateTime;

	public static void updateTime() {
		// Experimental "Lazy time uniforms" (Emberlens): read the clock on first use in this frame instead.
		dateTime = net.coderbot.iris.config.ExperimentalConfig.lazyTimeUniforms ? null : LocalDateTime.now();
	}

	private static LocalDateTime dateTime() {
		LocalDateTime now = dateTime;
		if (now == null) {
			dateTime = now = LocalDateTime.now();
		}
		return now;
	}

	public static void addTimeUniforms(UniformHolder uniforms) {
		Vector3i date = new Vector3i();
		Vector3i time = new Vector3i();
		Vector2i yearTime = new Vector2i();
		uniforms.uniform3i(UniformUpdateFrequency.PER_TICK, "currentDate", () -> {
			LocalDateTime now = dateTime();
			return date.set(now.getYear(), now.getMonthValue(), now.getDayOfMonth());
		});
		uniforms.uniform3i(UniformUpdateFrequency.PER_TICK, "currentTime", () -> {
			LocalDateTime now = dateTime();
			return time.set(now.getHour(), now.getMinute(), now.getSecond());
		});
		uniforms.uniform2i(UniformUpdateFrequency.PER_TICK, "currentYearTime", () -> {
			LocalDateTime now = dateTime();
			int secondOfYear = ((now.getDayOfYear() - 1) * 86400) + (now.getHour() * 3600) + (now.getMinute() * 60) + now.getSecond();
			return yearTime.set(secondOfYear, (now.toLocalDate().lengthOfYear() * 86400) - secondOfYear);
		});
	}

	static {
	}
}
