package net.coderbot.iris.gui.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.coderbot.iris.config.ExperimentalConfig;
import net.coderbot.iris.config.ExperimentalConfig.Option;
import net.coderbot.iris.gui.GuiUtil;
import net.coderbot.iris.gui.element.IrisObjectSelectionList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Global experimental options (Emberlens ports) and the pass timing tool. Opened from the shader pack screen.
 * Every change is saved at once to config/oculus-experimental.properties.
 */
public class ExperimentalScreen extends Screen {
	private static final Component SUBTITLE = Component.literal("Optimizations ported from Emberlens. All off by default. Hover an option for details.")
		.withStyle(ChatFormatting.GRAY);
	private static final int TOOLTIP_WIDTH = 260;

	private final Screen parent;
	private OptionList list;
	@Nullable
	private Option hoveredOption;

	public ExperimentalScreen(Screen parent) {
		super(Component.literal("Experimental"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.list = new OptionList(this, this.minecraft, this.width, this.height, 32, this.height - 36);
		this.addRenderableWidget(this.list);

		int left = this.width / 2 - 184;
		this.addRenderableWidget(new Button(left, this.height - 27, 150, 20,
			Component.literal("Enable safe optimizations"), button -> {
				ExperimentalConfig.enableSafe();
				ExperimentalConfig.save();
			}));
		this.addRenderableWidget(new Button(left + 154, this.height - 27, 110, 20,
			Component.literal("Reset to defaults"), button -> {
				ExperimentalConfig.resetAll();
				ExperimentalConfig.save();
			}));
		this.addRenderableWidget(new Button(left + 268, this.height - 27, 100, 20,
			CommonComponents.GUI_DONE, button -> this.onClose()));
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(poseStack);
		this.hoveredOption = null;
		super.render(poseStack, mouseX, mouseY, partialTick);
		drawCenteredString(poseStack, this.font, this.title, this.width / 2, 8, 0xFFFFFF);
		drawCenteredString(poseStack, this.font, SUBTITLE, this.width / 2, 20, 0xFFFFFF);

		if (this.hoveredOption != null) {
			this.renderTooltip(poseStack, tooltipFor(this.hoveredOption), mouseX, mouseY);
		}
	}

	private List<FormattedCharSequence> tooltipFor(Option option) {
		List<FormattedCharSequence> lines = new ArrayList<>();
		lines.addAll(this.font.split(Component.literal(option.name).withStyle(ChatFormatting.YELLOW), TOOLTIP_WIDTH));
		lines.addAll(this.font.split(Component.literal(option.description), TOOLTIP_WIDTH));
		lines.addAll(this.font.split(Component.literal("Benefit: ").withStyle(ChatFormatting.AQUA)
			.append(Component.literal(option.benefit).withStyle(ChatFormatting.WHITE)), TOOLTIP_WIDTH));
		MutableComponent risk = Component.literal("Risk: ").withStyle(ChatFormatting.AQUA)
			.append(Component.literal(option.risk.label).withStyle(style -> style.withColor(TextColor.fromRgb(option.risk.color))))
			.append(Component.literal(". " + option.riskNote).withStyle(ChatFormatting.WHITE));
		lines.addAll(this.font.split(risk, TOOLTIP_WIDTH));
		lines.addAll(this.font.split(Component.literal(option.applies + ". Right-click or shift-click cycles backwards.").withStyle(ChatFormatting.GRAY), TOOLTIP_WIDTH));
		return lines;
	}

	@Override
	public void onClose() {
		ExperimentalConfig.save();
		this.minecraft.setScreen(this.parent);
	}

	private static Component buttonText(Option option) {
		ChatFormatting valueColor = option.isOn() ? ChatFormatting.GREEN : ChatFormatting.GRAY;
		return Component.literal(option.name + ": ").append(Component.literal(option.valueName()).withStyle(valueColor));
	}

	private static class OptionList extends IrisObjectSelectionList<OptionEntry> {
		OptionList(ExperimentalScreen screen, Minecraft client, int width, int height, int top, int bottom) {
			super(client, width, height, top, bottom, 0, width, 24);

			String group = null;
			List<Option> row = new ArrayList<>();
			for (Option option : ExperimentalConfig.OPTIONS) {
				if (!option.group.equals(group)) {
					if (!row.isEmpty()) {
						this.addEntry(new RowEntry(screen, row));
						row = new ArrayList<>();
					}
					group = option.group;
					this.addEntry(new HeaderEntry(Component.literal(group).withStyle(ChatFormatting.YELLOW)));
				}
				row.add(option);
				if (row.size() == 2) {
					this.addEntry(new RowEntry(screen, row));
					row = new ArrayList<>();
				}
			}
			if (!row.isEmpty()) {
				this.addEntry(new RowEntry(screen, row));
			}
		}

		@Override
		public int getRowWidth() {
			return Math.min(320, this.width - 20);
		}
	}

	private abstract static class OptionEntry extends ObjectSelectionList.Entry<OptionEntry> {
	}

	private static class HeaderEntry extends OptionEntry {
		private final Component text;

		HeaderEntry(Component text) {
			this.text = text;
		}

		@Override
		public void render(PoseStack poseStack, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
			Font font = Minecraft.getInstance().font;
			drawCenteredString(poseStack, font, this.text, x + entryWidth / 2, y + 8, 0xFFFFFF);
		}

		@Override
		public Component getNarration() {
			return this.text;
		}
	}

	private static class RowEntry extends OptionEntry {
		private final ExperimentalScreen screen;
		private final List<Option> options;
		private int lastX;
		private int lastWidth;

		RowEntry(ExperimentalScreen screen, List<Option> options) {
			this.screen = screen;
			this.options = options;
		}

		private static int columnWidth(int entryWidth) {
			return (entryWidth - 4) / 2;
		}

		private int columnAt(double mouseX) {
			int columnWidth = columnWidth(this.lastWidth);
			if (mouseX >= this.lastX && mouseX < this.lastX + columnWidth) {
				return 0;
			}
			if (mouseX >= this.lastX + columnWidth + 4 && mouseX < this.lastX + 2 * columnWidth + 4) {
				return 1;
			}
			return -1;
		}

		@Override
		public void render(PoseStack poseStack, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
			this.lastX = x;
			this.lastWidth = entryWidth;
			int columnWidth = columnWidth(entryWidth);
			Font font = Minecraft.getInstance().font;
			int hoveredColumn = hovered && mouseY >= y && mouseY < y + 20 ? columnAt(mouseX) : -1;

			for (int i = 0; i < this.options.size(); i++) {
				Option option = this.options.get(i);
				int buttonX = x + i * (columnWidth + 4);
				boolean buttonHovered = hoveredColumn == i;
				GuiUtil.bindIrisWidgetsTexture();
				GuiUtil.drawButton(poseStack, buttonX, y, columnWidth, 20, buttonHovered, false);
				drawCenteredString(poseStack, font, buttonText(option), buttonX + columnWidth / 2, y + 6, 0xFFFFFF);
				if (buttonHovered) {
					this.screen.hoveredOption = option;
				}
			}
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			int column = columnAt(mouseX);
			if (column < 0 || column >= this.options.size() || (button != 0 && button != 1)) {
				return false;
			}
			this.options.get(column).cycle(button == 1 || Screen.hasShiftDown());
			ExperimentalConfig.apply();
			ExperimentalConfig.save();
			GuiUtil.playButtonClickSound();
			return true;
		}

		@Override
		public Component getNarration() {
			StringBuilder text = new StringBuilder();
			for (Option option : this.options) {
				if (text.length() > 0) {
					text.append(", ");
				}
				text.append(option.name).append(": ").append(option.valueName());
			}
			return Component.literal(text.toString());
		}
	}
}
