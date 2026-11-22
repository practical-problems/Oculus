package net.coderbot.iris.compat.dh.mixin;

import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.newshader.NewWorldRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * DH 3.x merges its rendered LODs into Minecraft's framebuffer with the copy shader
 * (GlDhCopyShader_forge#renderToFrameBuffer). While a shader pack is running, send that copy
 * into the pack's gbuffer framebuffer instead of the vanilla main target.
 * (DH 2.x did the same through DhApplyShader; that class no longer exists in 3.x.)
 */
@Mixin(targets = "com.seibel.distanthorizons.common.render.openGl.postProcessing.copy.GlDhCopyShader_forge", remap = false)
public class MixinDHCopyShader {
   @Redirect(
      method = {"renderToFrameBuffer"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/seibel/distanthorizons/core/wrapperInterfaces/minecraft/IMinecraftRenderWrapper;getTargetFramebuffer()I"
      ),
      remap = false
   )
   private int oculus$changeFB(IMinecraftRenderWrapper instance) {
      return Iris.getPipelineManager().getPipelineNullable() instanceof NewWorldRenderingPipeline pipeline
         ? pipeline.getDHCompat().getFramebuffer()
         : instance.getTargetFramebuffer();
   }
}
