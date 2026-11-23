package net.coderbot.iris.compat.dh.mixin;

import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import net.coderbot.iris.Iris;
import net.coderbot.iris.compat.dh.DHCompat;
import net.coderbot.iris.pipeline.newshader.NewWorldRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fallback for shader packs that do not provide DH programs (no {@code dh_terrain}).
 * <p>
 * With such a pack the full DH integration is off, so DH renders with its own shader and merges the result
 * into Minecraft's framebuffer with the copy shader ({@code GlDhCopyShader_forge#renderToFrameBuffer}). The pack
 * draws its own passes into its own buffers, so that vanilla target would not show the LODs. Send the copy into
 * the pack's colortex0 gbuffer framebuffer instead (no depth, same as the original Oculus DH support).
 * <p>
 * When the integration is active, {@code MixinDHApplyShader} cancels the copy before it gets here.
 */
@Mixin(targets = "com.seibel.distanthorizons.common.render.openGl.postProcessing.copy.GlDhCopyShader_forge", remap = false)
public class MixinDHCopyShader {
	@Redirect(
		method = {"renderToFrameBuffer"},
		at = @At(
			value = "INVOKE",
			target = "Lcom/seibel/distanthorizons/core/wrapperInterfaces/minecraft/IMinecraftRenderWrapper;getTargetFramebuffer()I"
		),
		remap = false,
		require = 1
	)
	private int oculus$changeFB(IMinecraftRenderWrapper instance) {
		if (!DHCompat.isActive() && Iris.getPipelineManager().getPipelineNullable() instanceof NewWorldRenderingPipeline pipeline) {
			int fallback = pipeline.getDhFallbackFramebufferId();
			if (fallback > 0) {
				return fallback;
			}
		}
		return instance.getTargetFramebuffer();
	}
}
